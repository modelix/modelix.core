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
import kotlin.collections.MutableMap
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
            println("server connected")
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            val userId = call.getUserName()

            var lastVersion: CLVersion? = null
            val deltaMutex = Mutex()
            val sendDelta: suspend (CLVersion, Map<String, String>?)->Unit = { newVersion, replacedIds ->
                deltaMutex.withLock {
                    if (newVersion.hash != lastVersion?.hash) {
                        send(MessageFromServer(
                            version = versionAsJson(newVersion, lastVersion),
                            replacedIds = replacedIds?.ifEmpty { null }
                        ).toJson())
                        lastVersion = newVersion
                    }
                }
            }

            val listener = object : IKeyListener {
                override fun changed(key: String, value: String?) {
                    if (value == null) return
                    launch {
                        val newVersion = CLVersion.loadFromHash(value, client.storeCache)
                        sendDelta(newVersion, null)
                    }
                }
            }

            client.listen(repositoryId.getBranchReference().getKey(), listener)
            try {
                sendDelta(getCurrentVersion(repositoryId), null)
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            println("message on server: $text")
                            val message = MessageFromClient.fromJson(text)
                            if (message.changedNodes != null) {
                                val replacedIds = HashMap<String, String>()
                                val mergedVersion = applyUpdate(lastVersion!!, message.changedNodes!!, repositoryId, userId, replacedIds)
                                sendDelta(mergedVersion, replacedIds)
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
        updateData: List<NodeUpdateData>,
        repositoryId: RepositoryId,
        userId: String?,
        replacedIds: MutableMap<String, String>
    ): CLVersion {
        val branch = OTBranch(PBranch(baseVersion.tree, client.idGenerator), client.idGenerator, client.storeCache!!)
        branch.computeWriteT { t ->
            for (nodeData in updateData) {
                updateNode(nodeData, t, replacedIds)
            }
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

    private fun updateNode(nodeData: NodeUpdateData, t: IWriteTransaction, replacedIds: MutableMap<String, String>): Long {
        val nodeIdStr: String
        val nodeId: Long
        if (nodeData.temporaryNodeId != null) {
            nodeId = client.idGenerator.generate()
            nodeIdStr = nodeId.toString(16)
            replacedIds[nodeData.temporaryNodeId!!] = nodeIdStr
        } else {
            nodeIdStr = nodeData.nodeId ?: throw IllegalArgumentException("Node ID missing")
            nodeId = nodeIdStr.toLong(16)
        }

        if (!t.containsNode(nodeId)) {
            val parent = nodeData.parent ?: throw IllegalArgumentException("Node $nodeId doesn't exist, but no parent node is specified.")
            val index = nodeData.index ?: throw IllegalArgumentException("Node $nodeId doesn't exist, but no index is specified. You can use -1 to add it to the end.")
            val role = nodeData.role
            t.addNewChild(
                parent.toLong(16),
                role,
                index,
                nodeId,
                nodeData.concept?.let { ConceptReference(it) }
            )
        }
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
            val expectedChildren = childIdStrings.map { it.toLong(16) }
            val unexpected = (t.getChildren(nodeId, role).toSet() - expectedChildren.toSet())
            if (unexpected.isNotEmpty()) {
                unexpected.forEach { child ->
                    t.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, child)
                }

            }
            val actualChildren = t.getChildren(nodeId, role)
            if (actualChildren != expectedChildren) {
                expectedChildren.forEachIndexed { index, child ->
                    t.moveChild(nodeId, role, index, child)
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

                override fun containmentChanged(nodeId: Long) {}

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