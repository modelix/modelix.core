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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.IRole
import org.modelix.model.api.key
import org.modelix.model.api.remove
import org.modelix.model.api.serialize
import org.modelix.model.api.usesRoleIds
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
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.SetPropertyOpData
import org.modelix.model.server.api.SetReferenceOpData
import org.modelix.model.server.api.VersionData
import org.modelix.model.server.api.buildModelQuery
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

class LightModelServer @JvmOverloads constructor (val port: Int, val rootNode: INode, val ignoredRoles: Set<IRole> = emptySet(), additionalHealthChecks: List<IHealthCheck> = emptyList()) {

    companion object {
        private val LOG = mu.KotlinLogging.logger {  }
    }

    private var server: NettyApplicationEngine? = null
    private val sessions: MutableSet<SessionData> = Collections.synchronizedSet(HashSet())
    private val ignoredRolesCache: MutableMap<IConceptReference, IgnoredRoles> = HashMap()
    private val healthChecks: List<IHealthCheck> = listOf(object : IHealthCheck {
        override val id: String = "readRootNode"
        override val enabledByDefault: Boolean = true

        override fun run(output: StringBuilder): Boolean {
            val count = getArea().executeRead { rootNode.allChildren.count() }
            output.appendLine("root node has $count children")
            return true
        }
    }) + additionalHealthChecks

    fun start() {
        LOG.trace { "server starting on port $port ..." }
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
                var session: SessionData? = null
                try {
                    session = SessionData(this)
                    sessions.add(session)
                    handleWebsocket(session)
                } catch (ex: Exception) {
                    LOG.error(ex) { "Error in websocket handler" }
                } finally {
                    sessions.remove(session)
                }
            }
            get("/health") {
                val output = StringBuilder()
                try {
                    val allChecks = healthChecks.associateBy { it.id }.toMap()
                    val enabledChecks = allChecks.filter { it.value.enabledByDefault }.keys.toMutableSet()

                    call.request.queryParameters.entries().forEach { entry ->
                        entry.value.forEach { value ->
                            if (!allChecks.containsKey(entry.key)) throw IllegalArgumentException("Unknown check: ${entry.key}")
                            if (value.toBooleanStrict()) {
                                enabledChecks.add(entry.key)
                            } else {
                                enabledChecks.remove(entry.key)
                            }
                        }
                    }
                    var isHealthy = true
                    for (healthCheck in allChecks.values) {
                        if (enabledChecks.contains(healthCheck.id)) {
                            output.appendLine("--- running check '${healthCheck.id}' ---")
                            val result = healthCheck.run(output)
                            output.appendLine()
                            output.appendLine("-> " + if (result) "successful" else "failed")
                            isHealthy = isHealthy && result
                        } else {
                            output.appendLine("--- check '${healthCheck.id}' is disabled. Use '/health?${healthCheck.id}=true' to enable it.")
                        }
                    }
                    if (isHealthy) {
                        call.respond(HttpStatusCode.OK, "healthy\n\n$output")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "unhealthy\n\n$output")
                    }
                } catch (ex: Exception) {
                    output.appendLine()
                    output.appendLine(ex.stackTraceToString())
                    call.respond(HttpStatusCode.InternalServerError, "unhealthy\n\n$output")
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
        LOG.trace { "New client connected" }
        withTimeout(10.seconds) {
            session.sendMessage(getArea().executeRead {
                session.createUpdateMessage()
            })
        }

        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val json = frame.readText()
                    LOG.trace { "incoming message: ${json.take(5000)}" }
                    val message = MessageFromClient.fromJson(json)
                    message.query?.let { session.replaceQuery(it) }
                    val ops = message.operations ?: emptyList()
                    val response = if (ops.isNotEmpty()) {
                        withContext(Dispatchers.Main) { // write access in MPS is only allowed from EDT
                            getArea().executeWrite { session.applyUpdate(ops, message.changeSetId) }
                        }
                    } else {
                        getArea().executeRead { session.applyUpdate(ops, message.changeSetId) }
                    }
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
        private var query: ModelQuery = buildModelQuery {}
        private val queryExecutor = IncrementalModelQueryExecutor(rootNode)

        @Synchronized
        fun replaceQuery(newQuery: ModelQuery) {
            query = newQuery
        }

        @Synchronized
        fun nodeChanged(node: INode) {
            queryExecutor.invalidate(setOf(node.reference))
        }

        @Synchronized
        fun createUpdate(): VersionData {
            val nodesToUpdate: MutableSet<INode> = HashSet()
            queryExecutor.update(query) { nodesToUpdate += it }
            val nodeDataList = nodesToUpdate.mapNotNull { try { it.toData() } catch (ex: Exception) { null } }
            return VersionData(
                repositoryId = null,
                versionHash = null,
                rootNodeId = rootNode.nodeId(),
                usesRoleIds = rootNode.usesRoleIds(),
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
        val conceptRef = concept?.getReference()
        val ignored = if (conceptRef == null) IgnoredRoles.EMPTY else ignoredRolesCache.getOrPut(conceptRef) {
            val ignoredChildRoles = concept?.getAllChildLinks()?.intersect(ignoredRoles)?.map { it.key(this) }?.toSet() ?: emptySet()
            val ignoredPropertyRoles = concept?.getAllProperties()?.intersect(ignoredRoles)?.map { it.key(this) }?.toSet() ?: emptySet()
            val ignoredReferenceRoles = concept?.getAllReferenceLinks()?.intersect(ignoredRoles)?.map { it.key(this) }?.toSet() ?: emptySet()
            IgnoredRoles(ignoredChildRoles, ignoredPropertyRoles, ignoredReferenceRoles).optimizeEmpty()
        }

        val childrenMap = LinkedHashMap<String?, List<NodeId>>()
        for (group in this.allChildren.groupBy { it.roleInParent }) {
            if (ignored.children.contains(group.key)) continue
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
            properties = this.getPropertyRoles().minus(ignored.properties)
                .associateWithNotNull { this.getPropertyValue(it) },
            references = this.getReferenceRoles().minus(ignored.references)
                .associateWithNotNull { this.getReferenceTargetRef(it)?.serialize() },
            children = childrenMap,
        )
    }

    interface IHealthCheck {
        val id: String
        val enabledByDefault: Boolean
        fun run(output: StringBuilder): Boolean
    }
}

private class IgnoredRoles(val children: Set<String>, val properties: Set<String>, val references: Set<String>) {
    fun isEmpty() = children.isEmpty() && properties.isEmpty() && references.isEmpty()
    fun optimizeEmpty() = if (isEmpty()) EMPTY else this
    companion object {
        val EMPTY = IgnoredRoles(emptySet(), emptySet(), emptySet())
    }
}

private fun INode.nodeId() = reference.serialize()
private fun INodeReference.nodeId() = serialize()

private fun <K, V : Any> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    return map { it to valueSelector(it) }.mapNotNull { p -> p.second?.let { Pair(p.first, it) } }.toMap()
}