/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresPermission
import org.modelix.model.IKeyListener
import org.modelix.model.VersionMerger
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.api.ChangeSetId
import org.modelix.model.server.api.ExceptionData
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import org.modelix.model.server.api.NodeData
import org.modelix.model.server.api.NodeUpdateData
import org.modelix.model.server.api.VersionData
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.associate
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.groupBy
import kotlin.collections.ifEmpty
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mapValues
import kotlin.collections.minus
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toSet
import kotlin.collections.toTypedArray

class JsonModelServer2(val client: LocalModelClient) {

    fun getStore() = client.storeCache!!

    fun init(application: Application) {
        application.apply {
            routing {
                requiresPermission("model-json-api", "read") {
                    route("/json/v2") {
                        initRouting()
                    }
                }
            }
        }
    }

    private fun getCurrentVersion(repositoryId: RepositoryId): CLVersion {
        val versionHash = client.asyncStore?.get(repositoryId.getBranchKey())!!
        return CLVersion.loadFromHash(versionHash, getStore())
    }

    private fun Route.initRouting() {
        webSocket("/{repositoryId}/ws") {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            val userId = call.getUserName()

            var lastVersion: CLVersion? = null
            val deltaMutex = Mutex()
            val sendDelta: suspend (CLVersion, Map<String, String>?, List<ChangeSetId>)->Unit = { newVersion, replacedIds, appliedChangeSets ->
                deltaMutex.withLock {
                    val sendMsg: suspend (MessageFromServer)->Unit = {
                        val text = it.toJson()
                        println("message to client: $text")
                        send(text)
                    }
                    if (newVersion.hash != lastVersion?.hash) {
                        sendMsg(MessageFromServer(
                            version = versionAsJson(newVersion, lastVersion),
                            replacedIds = replacedIds?.ifEmpty { null },
                            includedChangeSets = appliedChangeSets
                        ))
                        lastVersion = newVersion
                    } else if (!replacedIds.isNullOrEmpty() || appliedChangeSets.isNotEmpty()) {
                        sendMsg(MessageFromServer(
                            replacedIds = replacedIds?.ifEmpty { null },
                            includedChangeSets = appliedChangeSets
                        ))
                    }
                }
            }

            val listener = object : IKeyListener {
                override fun changed(key: String, value: String?) {
                    if (value == null) return
                    launch {
                        val newVersion = CLVersion.loadFromHash(value, client.storeCache)
                        sendDelta(newVersion, null, emptyList())
                    }
                }
            }

            client.listen(repositoryId.getBranchReference().getKey(), listener)
            try {
                sendDelta(getCurrentVersion(repositoryId), null, emptyList())
                val previouslyReplacedIds = HashMap<String, String>()
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            println("message on server: $text")
                            try {
                                val message = MessageFromClient.fromJson(text)
                                if (message.changedNodes != null) {
                                    val replacedIds = HashMap<String, String>()
                                    message.changedNodes!!
                                        .asSequence()
                                        .filter { it.nodeId == null }
                                        .mapNotNull { it.temporaryNodeId }
                                        .distinct()
                                        .filter { previouslyReplacedIds[it] == null }
                                        .forEach { replacedIds[it] = client.idGenerator.generate().toString(16) }
                                    val idsKnownByClient = HashSet<String>()
                                    val changedNodes = message.changedNodes!!
                                        .map { it.replaceIds { id ->
                                            idsKnownByClient.add(id)
                                            previouslyReplacedIds[id] ?: replacedIds[id]
                                        } }
                                        .associateBy { it.nodeId!!.toLong(16) }
                                    val mergedVersion = applyUpdate(lastVersion!!,
                                        changedNodes, repositoryId, userId)
                                    sendDelta(mergedVersion, replacedIds, listOfNotNull(message.changeSetId))
                                    previouslyReplacedIds.putAll(replacedIds)
                                    //idsKnownByClient.forEach { previouslyReplacedIds.remove(it) }
                                    // TODO remove all other entries in `previouslyReplacedIds` that were part of the same message
                                }
                            } catch (ex: Exception) {
                                send(MessageFromServer(exception = ExceptionData(RuntimeException("Failed to process message: $text", ex))).toJson())
                            }
                        }
                        else -> {}
                    }
                }
            } finally {
                client.removeListener(repositoryId.getBranchKey(), listener)
            }
        }
    }

    private fun applyUpdate(
        baseVersion: CLVersion,
        updateData: Map<Long, NodeUpdateData>,
        repositoryId: RepositoryId,
        userId: String?
    ): CLVersion {
        val branch = OTBranch(PBranch(baseVersion.tree, client.idGenerator), client.idGenerator, client.storeCache!!)
        branch.computeWriteT { t ->
            val postponedNodes = ArrayList<NodeUpdateData>()
            for (nodeData in updateData.values) {
                try {
                    val nodeId: Long = nodeData.nodeId!!.toLong(16)
                    if (t.containsNode(nodeId)) {
                        updateNode(nodeData, t, updateData)
                    } else {
                        // will be created when updating the parent
                        postponedNodes.add(nodeData)
                    }
                } catch (ex: Exception) {
                    throw RuntimeException("Failed to apply $nodeData", ex)
                }
            }
            val failedNodes = postponedNodes.filter { !t.containsNode(it.nodeId!!.toLong(16)) }
            if (failedNodes.isNotEmpty()) {
                throw RuntimeException("Nodes weren't created. Ensure they are used as a child in any of the other nodes:\n" +
                        failedNodes.joinToString("\n") { "\t$it" })
            }
            t.getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE).toList().forEach { t.deleteNode(it) }
        }

        val operationsAndTree = branch.operationsAndTree
        val newVersion = CLVersion.createRegularVersion(
            client.idGenerator.generate(),
            Date().toString(),
            userId,
            operationsAndTree.second as CLTree,
            baseVersion,
            operationsAndTree.first.map { it.getOriginalOp() }.toTypedArray()
        )
        val mergedVersion = VersionMerger(client.storeCache!!, client.idGenerator)
            .mergeChange(getCurrentVersion(repositoryId), newVersion)
        client.asyncStore!!.put(repositoryId.getBranchKey(), mergedVersion.hash)
        // TODO handle concurrent write to the branchKey, otherwise versions might get lost. See ReplicatedRepository.
        return mergedVersion
    }

    private fun updateNode(nodeData: NodeUpdateData, t: IWriteTransaction, allNodes: Map<Long, NodeUpdateData>): Long {
        val nodeId: Long = nodeData.nodeId!!.toLong(16)

        require(t.containsNode(nodeId)) { "Node ${nodeId.toString(16)} doesn't exist." }

        nodeData.properties?.forEach { (role, value) ->
            if (t.getProperty(nodeId, role) != value) {
                t.setProperty(nodeId, role, value)
            }
        }
        nodeData.references?.forEach { (role, newTargetIdStr) ->
            val newTargetId = newTargetIdStr?.toLong(16)
            val currentTarget = t.getReferenceTarget(nodeId, role)
            val currentTargetId: Long? = when (currentTarget) {
                is LocalPNodeReference -> currentTarget.id
                is PNodeReference -> if (currentTarget.branchId == t.tree.getId()) currentTarget.id else null
                else -> null
            }
            if (newTargetId != currentTargetId) {
                val newTarget = if (newTargetId == null) null else LocalPNodeReference(newTargetId)
                t.setReferenceTarget(nodeId, role, newTarget)
            }
        }
        nodeData.children?.forEach { (role, childIdStrings) ->
            val expectedChildren: List<Long> = childIdStrings.map { it.toLong(16) }
            val unexpected = (t.getChildren(nodeId, role).toSet() - expectedChildren.toSet())
            if (unexpected.isNotEmpty()) {
                unexpected.forEach { child ->
                    t.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, child)
                }

            }
            val actualChildren: List<Long> = t.getChildren(nodeId, role).toList()
            if (actualChildren != expectedChildren) {
                expectedChildren.forEachIndexed { index, child ->
                    if (t.containsNode(child)) {
                        t.moveChild(nodeId, role, index, child)
                    } else {
                        val childData = allNodes[child] ?: throw IllegalArgumentException("Data for node $child missing.")
                        t.addNewChild(nodeId, role, index, child, nodeData.concept?.let { ConceptReference(it) })
                        updateNode(childData, t, allNodes)
                    }
                }
            }
        }
        return nodeId
    }

    private fun versionAsJson(
        version: CLVersion,
        oldVersion: CLVersion?
    ): VersionData {
        val branch = TreePointer(version.tree)
        val nodeDataList = ArrayList<NodeData>()
        if (oldVersion == null) {
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            node2json(rootNode, true, nodeDataList)
        } else {
            val nodesToInclude = HashSet<Long>()
            version.tree.visitChanges(oldVersion.tree, object : ITreeChangeVisitorEx {
                override fun childrenChanged(nodeId: Long, role: String?) {
                    nodesToInclude += nodeId
                }

                override fun containmentChanged(nodeId: Long) {
                    nodesToInclude.add(nodeId)
                    if (version.tree.getParent(nodeId) == oldVersion.tree.getParent(nodeId)) {
                        // no childrenChanged event is received for the parent if only the role changed
                        nodesToInclude.add(version.tree.getParent(nodeId))
                    }
                }

                override fun propertyChanged(nodeId: Long, role: String) {
                    nodesToInclude += nodeId
                }

                override fun referenceChanged(nodeId: Long, role: String) {
                    nodesToInclude += nodeId
                }

                override fun nodeAdded(nodeId: Long) {
                    nodesToInclude += nodeId
                }

                override fun nodeRemoved(nodeId: Long) {}
            })
            nodesToInclude.forEach { node2json(PNodeAdapter(it, branch), false, nodeDataList) }
        }
        return VersionData(
            repositoryId = version.tree.getId(),
            versionHash = version.hash,
            rootNodeId = if (oldVersion == null) ITree.ROOT_ID.toString(16) else null,
            nodes = nodeDataList
        )
    }

    private fun node2json(node: INode, includeDescendants: Boolean, outputList: MutableList<NodeData>) {
        require(node is PNodeAdapter)
        val nodeId = node.nodeId.toString(16)

        val properties: Map<String, String> = node.getPropertyRoles()
            .map { it to node.getPropertyValue(it) }
            .filter { it.second != null }
            .associate { it.first to it.second!! }
        val references: Map<String, String> = node.getReferenceRoles().map {
            val targetRef = node.getReferenceTargetRef(it)
            val targetId = when (targetRef) {
                is LocalPNodeReference -> targetRef.id
                is PNodeReference -> if (targetRef.branchId == node.branch.getId()) targetRef.id else null
                else -> null
            }
            it to targetId?.toString(16)
        }.filter { it.second != null }.associate { it.first to it.second!! }
        val children = node.allChildren
            .groupBy { it.roleInParent }
            .mapValues { children -> children.value.map { (it as PNodeAdapter).nodeId.toString(16) } }
        val nodeData = NodeData(
            nodeId = nodeId,
            concept = node.getConceptReference()?.getUID(),
            parent = node.parent?.let { (it as PNodeAdapter).nodeId }?.toString(16),
            role = node.roleInParent,
            properties = properties,
            references = references,
            children = children
        )
        outputList.add(nodeData)

        if (includeDescendants) {
            node.allChildren.forEach { node2json(it, true, outputList) }
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {  }
    }
}