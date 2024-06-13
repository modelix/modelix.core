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
package org.modelix.model.server.handlers

import io.ktor.server.application.Application
import io.ktor.server.request.host
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.modelix.authorization.KeycloakScope
import org.modelix.authorization.asResource
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresPermission
import org.modelix.model.VersionMerger
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.api.AddNewChildNodeOpData
import org.modelix.model.server.api.ChangeSetId
import org.modelix.model.server.api.DeleteNodeOpData
import org.modelix.model.server.api.ExceptionData
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import org.modelix.model.server.api.MoveNodeOpData
import org.modelix.model.server.api.NodeData
import org.modelix.model.server.api.OperationData
import org.modelix.model.server.api.SetPropertyOpData
import org.modelix.model.server.api.SetReferenceOpData
import org.modelix.model.server.api.VersionData
import org.modelix.model.server.store.ContextScopedStoreClient
import org.modelix.model.server.store.LocalModelClient
import java.util.Date
import kotlin.collections.set

private val LOG = KotlinLogging.logger {}

class LightModelServer(val client: LocalModelClient, val repositoriesManager: RepositoriesManager) {

    fun init(application: Application) {
        application.routing {
            requiresPermission("model-json-api".asResource(), KeycloakScope.READ) {
                route("/json/v2") {
                    initRouting()
                }
            }
        }
    }

    private suspend fun getCurrentVersion(repositoryId: RepositoryId): CLVersion {
        val branch = repositoryId.getBranchReference()
        return checkNotNull(repositoriesManager.getVersion(branch)) {
            "Branch doesn't exist: $branch. Context Repo: ${ContextScopedStoreClient.CONTEXT_REPOSITORY.getAllValues()}, store: ${repositoriesManager.client.store}"
        }
    }

    private fun Route.initRouting() {
        webSocket("/{repositoryId}/ws") {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val userId = call.getUserName() ?: call.request.host()

                var lastVersionToClient: CLVersion? = null
                var lastVersionFromClient: CLVersion? = null
                val versionMerger = VersionMerger(client.storeCache, client.idGenerator)
                val deltaMutex = Mutex()
                val sendMsg: suspend (MessageFromServer) -> Unit = {
                    val text = it.toJson()
                    // println("message to client: $text")
                    send(text)
                }
                val branch = repositoryId.getBranchReference()
                val sendDelta: suspend (CLVersion, Map<String, String>?, ChangeSetId?) -> Unit = { newVersion, replacedIds, appliedChangeSet ->
                    require(deltaMutex.isLocked)
                    var mergedVersion: CLVersion =
                        if (lastVersionToClient == null) {
                            newVersion
                        } else {
                            versionMerger.mergeChange(
                                lastVersionToClient!!,
                                newVersion,
                            )
                        }
                    mergedVersion = repositoriesManager.mergeChanges(branch, mergedVersion.getContentHash())
                        .let { CLVersion.loadFromHash(it, mergedVersion.store) }
                    if (mergedVersion.getContentHash() != lastVersionToClient?.getContentHash()) {
                        sendMsg(
                            MessageFromServer(
                                version = versionAsJson(mergedVersion, lastVersionToClient),
                                replacedIds = replacedIds?.ifEmpty { null },
                                appliedChangeSet = appliedChangeSet,
                            ),
                        )
                        lastVersionToClient = mergedVersion
                    } else if (!replacedIds.isNullOrEmpty() || appliedChangeSet != null) {
                        sendMsg(
                            MessageFromServer(
                                replacedIds = replacedIds?.ifEmpty { null },
                                appliedChangeSet = appliedChangeSet,
                            ),
                        )
                    }
                }

                val versionChangeDetector = launch {
                    repositoriesManager.runWithRepository(repositoryId) {
                        try {
                            var lastKnownVersion: String? = null
                            while (true) {
                                val newHash = repositoriesManager.pollVersionHash(branch, lastKnownVersion)
                                if (newHash != lastKnownVersion) {
                                    lastKnownVersion = newHash
                                    val newVersion = CLVersion.loadFromHash(newHash, client.storeCache)
                                    deltaMutex.withLock {
                                        if (newHash != lastVersionToClient?.getContentHash()) {
                                            sendDelta(newVersion, null, null)
                                        }
                                    }
                                }
                            }
                        } catch (ex: Throwable) {
                            LOG.error("Polling $branch failed", ex)
                        }
                    }
                }

                try {
                    deltaMutex.withLock {
                        sendDelta(getCurrentVersion(repositoryId), null, null)
                    }
                    val previouslyReplacedIds = HashMap<String, String>()
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                // println("message on server: $text")
                                try {
                                    deltaMutex.withLock {
                                        val message = MessageFromClient.fromJson(text)
                                        if (message.operations != null) {
                                            val replacedIds = HashMap<String, String>()
                                            message.operations!!
                                                .asSequence()
                                                .filterIsInstance<AddNewChildNodeOpData>()
                                                .map { it.childId }
                                                .filter { previouslyReplacedIds[it] == null }
                                                .forEach { replacedIds[it] = client.idGenerator.generate().toString(16) }
                                            val idsKnownByClient = HashSet<String>()
                                            val operations = message.operations!!
                                                .map {
                                                    it.replaceIds { id ->
                                                        idsKnownByClient.add(id)
                                                        previouslyReplacedIds[id] ?: replacedIds[id]
                                                    }
                                                }
                                            val baseVersion = if (message.baseChangeSet == null) {
                                                lastVersionToClient
                                            } else {
                                                lastVersionFromClient ?: lastVersionToClient
                                            }

                                            val mergedVersion = applyUpdate(baseVersion!!, operations, repositoryId, userId)
                                            lastVersionFromClient = mergedVersion
                                            sendDelta(mergedVersion, replacedIds, message.changeSetId)
                                            previouslyReplacedIds.putAll(replacedIds)
                                            // idsKnownByClient.forEach { previouslyReplacedIds.remove(it) }
                                            // TODO remove all other entries in `previouslyReplacedIds` that were part of the same message
                                        }
                                    }
                                } catch (ex: Exception) {
                                    send(MessageFromServer(exception = ExceptionData(RuntimeException("Failed to process message: $text", ex))).toJson())
                                }
                            }
                            else -> {}
                        }
                    }
                } finally {
                    versionChangeDetector.cancel()
                }
            }
        }
    }

    private suspend fun applyUpdate(
        baseVersion: CLVersion,
        operations: List<OperationData>,
        repositoryId: RepositoryId,
        userId: String?,
    ): CLVersion {
        val branch = OTBranch(PBranch(baseVersion.getTree(), client.idGenerator), client.idGenerator, client.storeCache)
        branch.computeWriteT { t ->
            for (op in operations) {
                try {
                    when (op) {
                        is SetPropertyOpData -> {
                            if (t.getProperty(op.node.toLong(16), op.role) != op.value) {
                                t.setProperty(op.node.toLong(16), op.role, op.value)
                            }
                        }
                        is AddNewChildNodeOpData -> t.addNewChild(
                            parentId = op.parentNode.toLong(16),
                            role = op.role,
                            index = op.index,
                            concept = op.concept?.let { ConceptReference(it) },
                            childId = op.childId.toLong(16),
                        )
                        is DeleteNodeOpData -> t.deleteNode(op.nodeId.toLong(16))
                        is MoveNodeOpData -> t.moveChild(
                            newParentId = op.newParentNode.toLong(16),
                            newRole = op.newRole,
                            newIndex = op.newIndex,
                            childId = op.childId.toLong(16),
                        )
                        is SetReferenceOpData -> {
                            val newTargetId = op.target?.toLong(16)
                            val currentTarget = t.getReferenceTarget(op.node.toLong(16), op.role)
                            val currentTargetId: Long? = when (currentTarget) {
                                is LocalPNodeReference -> currentTarget.id
                                is PNodeReference -> if (currentTarget.branchId == t.tree.getId()) currentTarget.id else null
                                else -> null
                            }
                            if (newTargetId != currentTargetId) {
                                val newTarget = if (newTargetId == null) null else LocalPNodeReference(newTargetId)
                                t.setReferenceTarget(op.node.toLong(16), op.role, newTarget)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    throw RuntimeException("Failed to apply $op", ex)
                }
            }
            t.getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE).toList().forEach { t.deleteNode(it) }
        }

        val operationsAndTree = branch.getPendingChanges()
        val newVersion = CLVersion.createRegularVersion(
            client.idGenerator.generate(),
            Date().toString(),
            userId,
            operationsAndTree.second as CLTree,
            baseVersion,
            operationsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
        val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
            .mergeChange(getCurrentVersion(repositoryId), newVersion)
        client.asyncStore.put(repositoryId.getBranchKey(), mergedVersion.getContentHash())
        // TODO handle concurrent write to the branchKey, otherwise versions might get lost. See ReplicatedRepository.
        return mergedVersion
    }

    private fun versionAsJson(
        version: CLVersion,
        oldVersion: CLVersion?,
    ): VersionData {
        val branch = TreePointer(version.getTree())
        val nodeDataList = ArrayList<NodeData>()
        if (oldVersion == null) {
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            node2json(rootNode, true, nodeDataList)
        } else {
            val nodesToInclude = HashSet<Long>()
            version.getTree().visitChanges(
                oldVersion.getTree(),
                object : ITreeChangeVisitorEx {
                    override fun childrenChanged(nodeId: Long, role: String?) {
                        nodesToInclude += nodeId
                    }

                    override fun containmentChanged(nodeId: Long) {
                        nodesToInclude.add(nodeId)
                        if (version.getTree().getParent(nodeId) == oldVersion.getTree().getParent(nodeId)) {
                            // no childrenChanged event is received for the parent if only the role changed
                            nodesToInclude.add(version.getTree().getParent(nodeId))
                        }
                    }

                    override fun conceptChanged(nodeId: Long) {
                        nodesToInclude += nodeId
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
                },
            )
            nodesToInclude.forEach { node2json(PNodeAdapter(it, branch), false, nodeDataList) }
        }
        return VersionData(
            repositoryId = version.getTree().getId(),
            versionHash = version.getContentHash(),
            rootNodeId = if (oldVersion == null) ITree.ROOT_ID.toString(16) else null,
            usesRoleIds = version.getTree().usesRoleIds(),
            nodes = nodeDataList,
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
            children = children,
        )
        outputList.add(nodeData)

        if (includeDescendants) {
            node.allChildren.forEach { node2json(it, true, outputList) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <T> Channel<T>.receiveLast(): T {
    var latest = receive()
    while (!isEmpty) latest = receive()
    return latest
}
