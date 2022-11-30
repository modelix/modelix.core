/*
 * Copyright (c) 2022.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.light

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.remove
import org.modelix.model.api.serialize
import org.modelix.model.server.api.AddNewChildNodeOpData
import org.modelix.model.server.api.ChangeSetId
import org.modelix.model.server.api.DeleteNodeOpData
import org.modelix.model.server.api.ExceptionData
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import org.modelix.model.server.api.MoveNodeOpData
import org.modelix.model.server.api.NodeData
import org.modelix.model.server.api.NodeId
import org.modelix.model.server.api.OperationData
import org.modelix.model.server.api.SetPropertyOpData
import org.modelix.model.server.api.SetReferenceOpData
import org.modelix.model.server.api.VersionData
import java.time.Duration
import java.util.*

class LightModelServer(val port: Int, val rootNode: INode) {
    companion object {
        private val LOG = mu.KotlinLogging.logger {  }
    }

    private var server: NettyApplicationEngine? = null
    private val sessions: MutableSet<SessionData> = Collections.synchronizedSet(HashSet())

    fun start() {
        LOG.trace { "server starting ..." }
        server = embeddedServer(Netty, port = port) {
            init()
        }
        server!!.start()
        LOG.trace { "server started" }
    }

    fun stop() {
        LOG.trace { "server stopping ..." }
        server?.stop()
        server = null
        LOG.trace { "server stopped" }
    }

    fun nodeChanged(node: INode) {
        LOG.trace { "node changed: $node" }
        synchronized(sessions) {
            for (session in sessions) {
                synchronized(session) {
                    session.nodeChanged(node)
                }
            }
        }
    }

    fun sendUpdate() {
        val sessionsCopy = synchronized(sessions) { sessions.toList() }
        runBlocking {
            for (session in sessionsCopy) {
                launch {
                    session.sendUpdate()
                }
            }
        }
    }

    private fun getArea() = rootNode.getArea()

    private fun Application.init() {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            webSocket("/ws") {
                try {
                    val session = SessionData(this)
                    sessions.add(session)
                    handleWebsocket(session)
                } catch (ex: Exception) {
                    LOG.error(ex) { "Error in websocket handler" }
                }
            }
        }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Post)
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleWebsocket(session: SessionData) {
        session.sendMessage(getArea().executeRead {
            session.includeNodeIfSerializable(rootNode.reference)
//            rootNode.allChildren.forEach { session.includeNodeIfSerializable(it.reference) }
            session.createUpdateMessage()
        })

        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val json = frame.readText()
                    LOG.trace { "incoming message: ${json.take(5000)}" }
                    val message = MessageFromClient.fromJson(json)
                    val ops = message.operations ?: continue
                    val response = getArea().executeWrite { session.applyUpdate(ops, message.changeSetId) }
                    send(response.toJson())
                }
                else -> {}
            }
        }
    }

    private fun NodeId.resolveNode(): INode {
        return INodeReferenceSerializer.deserialize(this).resolveNode(getArea())
            ?: throw IllegalArgumentException("Node not found: $this")
    }

    inner class SessionData(val websocketSession: DefaultWebSocketServerSession) {
        private val cleanNodesOnClient: MutableSet<INodeReference> = HashSet()
        private val dirtyNodesOnClient: MutableSet<INodeReference> = HashSet()

        fun includeNode(node: INodeReference) {
            dirtyNodesOnClient.add(node)
        }

        fun includeNodeIfSerializable(node: INodeReference) {
            try {
                node.nodeId()
                includeNode(node)
            } catch (ex: Exception) {}
        }

        @Synchronized
        fun nodeChanged(node: INode) {
            val ref = node.reference
            if (cleanNodesOnClient.remove(ref)) {
                dirtyNodesOnClient.add(ref)
            }
        }

        @Synchronized
        fun createUpdate(): VersionData {
            val area = getArea()
            val unresolvedNodes = HashSet<INodeReference>()
            val nodeDataList = dirtyNodesOnClient.mapNotNull {
                val node = it.resolveNode(area)
                if (node == null) unresolvedNodes.add(it)
                node?.toData()
            }
            // TODO include descendants
            cleanNodesOnClient.addAll(dirtyNodesOnClient - unresolvedNodes)
            dirtyNodesOnClient.clear()
            return VersionData(
                repositoryId = null,
                versionHash = null,
                rootNodeId = rootNode.nodeId(),
                nodes = nodeDataList
            )
        }

        fun createUpdateMessage(): MessageFromServer {
            return MessageFromServer(
                version = createUpdate(),
                replacedIds = null,
                appliedChangeSet = null,
                exception = null
            )
        }

        suspend fun sendUpdate() {
            sendMessage(createUpdateMessage())
        }

        suspend fun sendMessage(message: MessageFromServer) {
            val json = message.toJson()
            LOG.trace { "outgoing message: ${json.take(5000)}" }
            websocketSession.send(json)
        }

        @Synchronized
        fun applyUpdate(operations: List<OperationData>, changeSetId: ChangeSetId?): MessageFromServer {
            try {
                val updateSession = UpdateSession()
                operations.forEach { updateSession.applyOperation(it) }
                dirtyNodesOnClient.addAll(updateSession.modifiedNodes)
                cleanNodesOnClient.removeAll(updateSession.modifiedNodes)
                return MessageFromServer(
                    version = createUpdate(),
                    replacedIds = updateSession.replacedIds,
                    appliedChangeSet = changeSetId,
                )
            } catch (ex: Exception) {
                return MessageFromServer(exception = ExceptionData(ex))
            }
        }

        private inner class UpdateSession() {
            val replacedIds: MutableMap<NodeId, NodeId> = HashMap()
            val modifiedNodes: MutableSet<INodeReference> = HashSet()

            private fun NodeId.replaceAndResolve(): INode {
                return (replacedIds[this] ?: this).resolveNode()
            }

            fun applyOperation(op: OperationData) {
                when (op) {
                    is AddNewChildNodeOpData -> {
                        val parent = op.parentNode.replaceAndResolve()
                        modifiedNodes.add(parent.reference)
                        val newNode = parent.addNewChild(
                            role = op.role,
                            index = op.index,
                            concept = op.concept?.let { ConceptReference(it) }
                        )
                        replacedIds[op.childId] = newNode.nodeId()
                    }
                    is DeleteNodeOpData -> {
                        val node = op.nodeId.replaceAndResolve()
                        node.parent?.reference?.let { modifiedNodes.add(it) }
                        node.remove()
                    }
                    is MoveNodeOpData -> {
                        val newParent = op.newParentNode.replaceAndResolve()
                        val child = op.childId.replaceAndResolve()
                        val oldParent = child.parent
                        modifiedNodes.add(newParent.reference)
                        modifiedNodes.add(child.reference)
                        oldParent?.reference?.let { modifiedNodes.add(it) }
                        newParent.moveChild(
                            role = op.newRole,
                            index = op.newIndex,
                            child = child
                        )
                    }
                    is SetPropertyOpData -> {
                        val node = op.node.replaceAndResolve()
                        modifiedNodes.add(node.reference)
                        node.setPropertyValue(op.role, op.value)
                    }
                    is SetReferenceOpData -> {
                        val node = op.node.replaceAndResolve()
                        modifiedNodes.add(node.reference)
                        node.setReferenceTarget(op.role, op.target?.resolveNode())
                    }
                }
            }
        }
    }

    private fun INode.toData(): NodeData {
        val childrenMap = LinkedHashMap<String?, List<NodeId>>()
        for (group in this.allChildren.groupBy { it.roleInParent }) {
            try {
                childrenMap[group.key] = group.value.map { it.nodeId() }
            } catch (ex: Exception) {
                LOG.warn { "Failed to serialize a child in ${this.concept?.getShortName()}.${group.key}. Excluding this role. ${ex.message}" }
            }
        }
        return NodeData(
            nodeId = this.nodeId(),
            concept = this.getConceptReference()?.getUID(),
            parent = this.parent?.reference?.serialize(),
            role = this.roleInParent,
            properties = this.getPropertyRoles().associateWithNotNull { this.getPropertyValue(it) },
            references = this.getReferenceRoles().associateWithNotNull { this.getReferenceTargetRef(it)?.serialize() },
            children = childrenMap,
        )
    }
}



private fun INode.nodeId() = reference.serialize()
private fun INodeReference.nodeId() = serialize()

private fun <K, V : Any> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    return map { it to valueSelector(it) }.mapNotNull { p -> p.second?.let { Pair(p.first, it) } }.toMap()
}