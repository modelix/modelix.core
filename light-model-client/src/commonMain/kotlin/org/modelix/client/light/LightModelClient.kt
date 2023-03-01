package org.modelix.client.light

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.delay
import org.modelix.model.api.*
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import org.modelix.model.server.api.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TEMP_ID_PREFIX = "tmp-"

class LightModelClient(val connection: IConnection, val debugName: String = "") {

    private val nodes: MutableMap<NodeId, NodeData> = HashMap()
    private val area = Area()
    private var repositoryId: String? = null
    private var lastMergedVersionHash: String? = null
    private val pendingOperations: MutableList<OperationData> = ArrayList()
    private var rootNodeId: NodeId? = null
    private var temporaryIdsSequence: Long = 0
    private var changeSetIdSequence: Int = 0
    private val nodesReferencingTemporaryIds = HashSet<NodeId>()
    private var synchronizationLevel: Int = 0
    private val temporaryNodeAdapters: MutableMap<String, NodeAdapter> = HashMap()
    private var initialized = false
    private var lastUnconfirmedChangeSetId: ChangeSetId? = null
    private val unappliedVersions: MutableList<VersionData> = ArrayList()
    private var exceptions: MutableList<ExceptionData> = ArrayList()
    private var currentAccessType: AccessType = AccessType.NONE
    private var unappliedQuery: ModelQuery? = null

    init {
        connection.connect { message ->
            try {
                messageReceived(message)
            } catch (ex: Exception) {
                LOG.error(ex) { "Failed to process message: $message" }
            }
        }
    }

    fun dispose() {
        connection.disconnect()
    }

    fun changeQuery(query: ModelQuery) {
        synchronized {
            LOG.trace { "Changing query to ${query.toJson()}" }
            if (initialized) {
                connection.sendMessage(MessageFromClient(query = query))
            } else {
                unappliedQuery = query
            }
        }
    }

    fun checkException() {
        val ex = exceptions.firstOrNull()
        if (ex != null) {
            exceptions.clear()
            throw ServerSideException(ex)
        }
    }

    private fun checkRead() {
        synchronized {
            if (!currentAccessType.canRead) throw IllegalStateException("Not in a read transaction")
        }
    }

    private fun checkWrite() {
        synchronized {
            if (!currentAccessType.canWrite) throw IllegalStateException("Not in a write transaction")
        }
    }

    fun isInSync(): Boolean = synchronized {
        checkException()
        lastUnconfirmedChangeSetId == null && pendingOperations.isEmpty() && unappliedVersions.isEmpty()
    }

    private fun <T> synchronizedRead(body: ()->T): T {
        return synchronized {
            checkRead()
            body()
        }
    }

    private fun <T> synchronizedWrite(body: ()->T): T {
        return synchronized {
            checkWrite()
            body()
        }
    }

    fun <T> runRead(body: () -> T): T = synchronized {
        if (currentAccessType.canRead) {
            body()
        } else {
            val previousType = currentAccessType
            currentAccessType = when (previousType) {
                AccessType.NONE, AccessType.WRITE -> AccessType.READ
                AccessType.READ -> throw RuntimeException("Read is already allowed")
            }
            try {
                body()
            } finally {
                currentAccessType = previousType
            }
        }
    }

    fun <T> runWrite(body: () -> T): T = synchronized {
        if (currentAccessType.canWrite) {
            body()
        } else if (currentAccessType.canRead) {
            throw IllegalStateException("Cannot run write from read")
        } else {
            val previousType = currentAccessType
            currentAccessType = AccessType.WRITE
            try {
                body()
            } finally {
                currentAccessType = previousType
                fullConsistencyCheck()
            }
        }
    }

    suspend fun waitForRootNode(timeout: Duration = 5.seconds, coroutineDelay: Duration = 10.milliseconds): INode? {
        var result : INode? = null
        kotlinx.coroutines.withTimeout(timeout) {
            while (true) {
                checkException()
                val node = runRead { getRootNode() }
                if (node != null && runRead { node.isValid }) {
                    runRead {
                        result = node
                    }
                    break
                }
                delay(coroutineDelay)
            }
        }
        return result
    }

    fun getRepositoryId(): String? = repositoryId

    fun getRootNode(): INode? {
        return synchronizedRead {
            rootNodeId?.let { getNodeAdapter(it) }
        }
    }

    fun getNodeIfLoaded(nodeId: NodeId): INode? {
        return synchronized {
            if (nodes.containsKey(nodeId)) getNodeAdapter(nodeId) else null
        }
    }

    fun isInitialized(): Boolean = synchronized { initialized }

    private fun fullConsistencyCheck() {
//        runRead {
//            nodes.keys.forEach { getNode(it).checkContainmentConsistency() }
//
//            val actualTempReferences = nodes.filter { it.value.allReferencedIds().any { it.startsWith(TEMP_ID_PREFIX) } }.keys
//            val registeredTempReferences = nodesReferencingTemporaryIds
//            val unregisteredTempReferences = actualTempReferences - registeredTempReferences
//            val wrongRegistered = registeredTempReferences - actualTempReferences
//            if (unregisteredTempReferences.isNotEmpty()) {
//                throw RuntimeException("missing registrations: $unregisteredTempReferences, unnecessary registration: $wrongRegistered")
//            }
//        }
    }

    fun hasTemporaryIds(): Boolean = synchronized {
        temporaryNodeAdapters.isNotEmpty() || nodesReferencingTemporaryIds.isNotEmpty()
    }

    fun getNode(nodeId: NodeId): NodeAdapter {
        return synchronizedRead {
            getNodeData(nodeId) // fail fast if it doesn't exist
            return@synchronizedRead getNodeAdapter(nodeId)
        }
    }

    private fun generateTemporaryNodeId(): String = synchronized {
        TEMP_ID_PREFIX + (++temporaryIdsSequence).toString(16)
    }
    
    private fun <R> synchronized(block: () -> R): R {
        runSynchronized(this) {
            try {
                synchronizationLevel++
                val result = block()
                return result
            } finally {
                synchronizationLevel--
                if (synchronizationLevel == 0) {
                    flush()
                }
            }
        }
    }

    private fun flush() {
        if (pendingOperations.isNotEmpty()) {
            val changeSetId: ChangeSetId = ++changeSetIdSequence
            lastUnconfirmedChangeSetId = changeSetId
            val message = MessageFromClient(
                operations = ArrayList(pendingOperations),
                changeSetId = changeSetId,
                baseVersionHash = lastMergedVersionHash,
                baseChangeSet = lastUnconfirmedChangeSetId
            )
            //println("message to server: " + message.toJson())
            connection.sendMessage(message)
            pendingOperations.clear()
        }
    }

    private fun messageReceived(message: MessageFromServer) {
        synchronized {
            // println("$debugName processing on client: " + message.toJson())
            if (message.exception != null) {
                exceptions.add(message.exception!!)
            }
            message.replacedIds?.let { replaceIds(it) }
            if (lastUnconfirmedChangeSetId != null && message.appliedChangeSet == lastUnconfirmedChangeSetId) {
                lastUnconfirmedChangeSetId = null
            }
            message.version?.let { unappliedVersions.add(it) }
            if (lastUnconfirmedChangeSetId == null && unappliedVersions.isNotEmpty()) {
                val merged = unappliedVersions.reduce { old, new -> new.merge(old) }
                applyVersion(merged)
                unappliedVersions.clear()
            }
            if (unappliedQuery != null) {
                connection.sendMessage(MessageFromClient(query = unappliedQuery))
                unappliedQuery = null
            }
            fullConsistencyCheck()
        }
    }

    private fun applyVersion(version: VersionData) {
        synchronized {
            initialized = true
            if (version.repositoryId != null && version.repositoryId != repositoryId) {
                repositoryId = version.repositoryId
            }
            if (version.rootNodeId != null && version.rootNodeId != rootNodeId) {
                rootNodeId = version.rootNodeId
            }
            lastMergedVersionHash = version.versionHash

            val oldChildNodes: Set<NodeId> = version.nodes.mapNotNull { nodes[it.nodeId] }.flatMap { it.children.values.flatten() }.toSet()
            for (nodeData in version.nodes) {
                nodes[nodeData.nodeId] = nodeData
            }

            val newChildNodes: Set<NodeId> = version.nodes.flatMap { it.children.values.flatten() }.toSet()
            val removedNodes: Set<NodeId> = oldChildNodes - newChildNodes
            removedNodes.forEach { removeDataRecursive(it, newChildNodes) }
        }
    }

    private fun removeDataRecursive(nodeId: NodeId, nodesToKeep: Set<NodeId>) {
        if (nodesToKeep.contains(nodeId)) return
        val data = nodes[nodeId] ?: return
        data.children.values.flatten().forEach { removeDataRecursive(it, nodesToKeep) }
        nodes.remove(nodeId)
        //println("Removed $nodeId: $data")
    }

    private fun replaceIds(replacements: Map<String, String>) {
        synchronized {
            require(pendingOperations.isEmpty()) { "Flush pending updates first" }
            val nodesReferencingTemporaryIdsSaved = ArrayList(nodesReferencingTemporaryIds)
            val noTempReferencesLeft = ArrayList<String>(nodesReferencingTemporaryIds.size)
            for (sourceId in nodesReferencingTemporaryIds) {
                val oldData = nodes[sourceId] ?: continue
                val newData = oldData.replaceReferences { references ->
                    references.map { it.key to (replacements[it.value] ?: it.value) }.toMap()
                }.replaceChildren { children ->
                    children.mapValues { role2list -> role2list.value.map { child -> replacements[child] ?: child } }
                }.let { it.replaceContainment(replacements[it.parent] ?: it.parent, it.role) }
                nodes[sourceId] = newData
                if (newData.references.values.all { !it.startsWith(TEMP_ID_PREFIX) }
                    && newData.children.values.flatten().all { !it.startsWith(TEMP_ID_PREFIX) }
                    && newData.parent?.startsWith(TEMP_ID_PREFIX) != true) {
                    noTempReferencesLeft.add(sourceId)
                }
            }
            nodesReferencingTemporaryIds.removeAll(noTempReferencesLeft)
            for (replacement in replacements) {
                val oldData: NodeData? = nodes.remove(replacement.key)
                if (oldData != null) {
                    nodes[replacement.value] = oldData.replaceId(replacement.value)
                }
                val tempAdapter = temporaryNodeAdapters.remove(replacement.key)
                if (tempAdapter != null) {
                    tempAdapter.nodeId = replacement.value
                }
            }
            for (i in pendingOperations.indices) {
                pendingOperations[i] = pendingOperations[i].replaceIds { id -> replacements[id] }
            }
            nodesReferencingTemporaryIds.removeAll(replacements.keys)
            nodesReferencingTemporaryIds.addAll(replacements.values)

            runRead {
                replacements.values.mapNotNull { nodes[it] }.forEach { getNode(it.nodeId).checkContainmentConsistency() }
            }
            fullConsistencyCheck()
        }
    }

    private fun getNodeData(id: NodeId): NodeData {
        return synchronized {
            nodes[id]
                ?: throw IllegalArgumentException("Node with ID $id doesn't exist or wasn't loaded.")
        }
    }

    private fun getNodeAdapter(nodeId: NodeId): NodeAdapter {
        return synchronized {
            if (nodeId.startsWith(TEMP_ID_PREFIX)) {
                temporaryNodeAdapters.getOrPut(nodeId) { NodeAdapter(nodeId) }
            } else {
                NodeAdapter(nodeId)
            }
        }
    }

    inner class NodeAdapter(var nodeId: NodeId) : INode, INodeReference {
        fun getData() = synchronizedRead { getNodeData(nodeId) }

        override fun resolveNode(area: IArea?): INode? = this

        override fun getArea(): IArea = area

        override val isValid: Boolean
            get() = synchronizedRead { nodes.containsKey(nodeId) }
        override val reference: INodeReference
            get() = this
        override val concept: IConcept?
            get() = synchronizedRead { getConceptReference()?.resolve() }
        override val roleInParent: String?
            get() = synchronizedRead { getData().role }
        override val parent: NodeAdapter?
            get() = synchronizedRead { getData().parent?.let { getNodeAdapter(it) } }

        override fun getChildren(role: String?): List<NodeAdapter> {
            return synchronizedRead { getData().children[role]?.map { getNodeAdapter(it) } ?: emptyList() }
        }

        override val allChildren: List<NodeAdapter>
            get() = synchronizedRead { getData().children.flatMap { it.value }.map { getNodeAdapter(it) } }

        override fun getConceptReference(): IConceptReference? {
            return synchronizedRead { getData().concept?.let { ConceptReference(it) } }
        }

        override fun moveChild(role: String?, index: Int, child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Not part of this client: $child" }
            synchronizedWrite {
                require(!getAncestors(includeSelf = true).contains(child)) { "Attempt to create a cyclic containment" }
                val oldParent = child.parent!! as NodeAdapter
                val oldRole = child.roleInParent
                val oldIndex = oldParent.getChildren(role).indexOf(child)
                val sameParent = oldParent == this
                val insertAt = if (sameParent && role == oldRole && oldIndex < index) index - 1 else index

                nodes[oldParent.nodeId] = oldParent.getData().replaceChildren(oldRole) { oldChildren ->
                    oldChildren - child.nodeId
                }
                nodes[nodeId] = getData().replaceChildren(role) { oldChildren ->
                    val newChildren = oldChildren.toMutableList()
                    if (insertAt == -1) newChildren.add(child.nodeId) else newChildren.add(insertAt, child.nodeId)
                    newChildren
                }
                nodes[child.nodeId] = child.getData().replaceContainment(nodeId, role)
                pendingOperations.add(MoveNodeOpData(nodeId, role, index, child.nodeId))
                if (nodeId.startsWith(TEMP_ID_PREFIX)) nodesReferencingTemporaryIds.add(child.nodeId)
                if (child.nodeId.startsWith(TEMP_ID_PREFIX)) nodesReferencingTemporaryIds.add(nodeId)
                checkContainmentConsistency()
                oldParent.checkContainmentConsistency()
                child.checkContainmentConsistency()
            }
        }

        override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
            return addNewChild(role, index, concept?.getReference())
        }

        override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
            return synchronizedWrite {
                val size = getChildren(role).size
                require(index == -1 || index in (0..size)) { "Invalid index: $index, size: $size" }
                val childId = generateTemporaryNodeId()
                val oldParentData = getData()
                val newParentData = oldParentData.replaceChildren(role) { oldChildren ->
                    val newChildren = oldChildren.toMutableList()
                    if (index == -1) {
                        newChildren.add(childId)
                    } else {
                        newChildren.add(index, childId)
                    }
                    newChildren
                }
                nodes[childId] = NodeData(childId, concept?.getUID(), nodeId, role, emptyMap(), emptyMap(), emptyMap())
                nodes[nodeId] = newParentData
                nodesReferencingTemporaryIds.add(nodeId)
                if (nodeId.startsWith(TEMP_ID_PREFIX)) nodesReferencingTemporaryIds.add(childId)
                pendingOperations.add(AddNewChildNodeOpData(nodeId, role, index, concept?.getUID(), childId))
                val childNode = getNodeAdapter(childId)
                checkContainmentConsistency()
                childNode.checkContainmentConsistency()
                return@synchronizedWrite childNode
            }
        }

        override fun removeChild(child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Unsupported child type: $child" }
            synchronizedWrite {
                require(child.parent == this) { "$child is not a child of $this" }
                require(child.allChildren.isEmpty()) { "$child is not empty" }

                val childId = child.nodeId

                // remove broken references
                if (childId.startsWith(TEMP_ID_PREFIX)) {
                    for (sourceNodeId in nodesReferencingTemporaryIds) {
                        val sourceNodeData = nodes[sourceNodeId] ?: continue
                        val brokenRoles = sourceNodeData.references.filter { it.value == childId }.map { it.key }
                        if (brokenRoles.isNotEmpty()) {
                            val sourceNode = getNode(sourceNodeId)
                            brokenRoles.forEach { brokenRole ->
                                //println("removing reference $sourceNodeId.$brokenRole -> $childId")
                                sourceNode.setReferenceTarget(brokenRole, null as INodeReference?)
                            }
                        }
                    }
                }

                val oldParentData = getData()
                val role = child.roleInParent
                val childrenInRole = oldParentData.children[role] ?: emptyList()
                if (!childrenInRole.contains(childId)) {
                    throw RuntimeException("$childId not found in $this.$role: $childrenInRole")
                }
                val newParentData = oldParentData.replaceChildren(role) { it - childId }
                nodes[nodeId] = newParentData
                pendingOperations.add(DeleteNodeOpData(childId))
                nodes.remove(childId)
                //pendingUpdates.removeAll { it.nodeId == childId }
                nodesReferencingTemporaryIds.remove(childId)
                checkContainmentConsistency()
            }
        }

        fun checkContainmentConsistency() {
            val parentNode = this.parent
            if (parentNode == null) {
                require(this.roleInParent == null) { "$this has no parent, but a role: $roleInParent" }
            } else {
                val siblings = parentNode.getChildren(this.roleInParent).map { it.nodeId }.toSet()
                require(siblings.contains(this.nodeId)) { "$this not found in $parent.$roleInParent: $siblings" }
            }
            for ((role, childrenInRole) in getData().children) {
                for (childId in childrenInRole) {
                    val childNode = getNode(childId)
                    require(childNode.roleInParent == role && childNode.parent == this) {
                        "$childId found in $this.$role, but actual containment is ${childNode.parent}.${childNode.roleInParent}"
                    }
                }
            }
        }

        override fun getReferenceTarget(role: String): NodeAdapter? {
            return getReferenceTargetRef(role)
        }

        override fun getReferenceTargetRef(role: String): NodeAdapter? {
            return synchronizedRead { getData().references[role]?.let { NodeAdapter(it) } }
        }

        override fun setReferenceTarget(role: String, target: INodeReference?) {
            synchronizedWrite {
                val targetId: NodeId? = when (target) {
                    null -> null
                    is NodeAdapter -> target.nodeId
                    is NodeReferenceById -> target.nodeId
                    else -> throw IllegalArgumentException("Unsupported reference: $target")
                }
                nodes[nodeId] = getData().replaceReferences {
                    if (targetId == null) it - role else it + (role to targetId)
                }
                pendingOperations.add(SetReferenceOpData(nodeId, role, targetId))
                if (targetId != null && targetId.startsWith(TEMP_ID_PREFIX)) {
                    nodesReferencingTemporaryIds.add(nodeId)
                }
            }
        }

        override fun setReferenceTarget(role: String, target: INode?) {
            synchronizedWrite {  setReferenceTarget(role, target?.reference) }
        }

        override fun getPropertyValue(role: String): String? {
            return getData().properties[role]
        }

        override fun setPropertyValue(propertyRole: String, value: String?) {
            synchronizedWrite {
                val oldData = getData()
                nodes[nodeId] = with(oldData) {
                    NodeData(
                        nodeId = nodeId,
                        concept = concept,
                        parent = parent,
                        role = role,
                        properties = if (value == null) properties - propertyRole else properties + (propertyRole to value),
                        references = references,
                        children = children
                    )
                }
                pendingOperations.add(SetPropertyOpData(nodeId, propertyRole, value))
            }
        }

        override fun getPropertyRoles(): List<String> {
            return synchronizedRead { getData().properties.keys.toList() }
        }

        override fun getReferenceRoles(): List<String> {
            return synchronizedRead { getData().references.keys.toList() }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NodeAdapter

            val sameNodeId = synchronized { nodeId == other.nodeId }
            if (!sameNodeId) return false
            if (getClient() != other.getClient()) return false

            return true
        }

        override fun hashCode(): Int {
            if (nodeId.startsWith(TEMP_ID_PREFIX)) {
                throw IllegalStateException("The server hasn't yet assigned an ID to this node." +
                        " The ID and the hashCode will change.")
            }
            return nodeId.hashCode()
        }

        fun getClient(): LightModelClient = this@LightModelClient

        override fun toString(): String {
            return nodeId
        }
    }

    inner class Area : IArea {
        override fun getRoot(): INode {
            return synchronizedRead {rootNodeId?.let { getNodeAdapter(it) } ?: throw IllegalStateException("Root node ID unknown") }
        }

        @Deprecated("use ILanguageRepository.resolveConcept")
        override fun resolveConcept(ref: IConceptReference): IConcept? {
            TODO("Not yet implemented")
        }

        override fun resolveNode(ref: INodeReference): INode? {
            return synchronizedRead { resolveOriginalNode(ref) }
        }

        override fun resolveOriginalNode(ref: INodeReference): INode? {
            return synchronizedRead {
                when (ref) {
                    is NodeAdapter -> ref
                    else -> null
                }
            }
        }

        override fun resolveBranch(id: String): IBranch? {
            return null
        }

        override fun collectAreas(): List<IArea> {
            return listOf(this)
        }

        override fun getReference(): IAreaReference {
            return AreaReference(repositoryId)
        }

        override fun resolveArea(ref: IAreaReference): IArea? {
            return if (ref is AreaReference && ref.branchId == repositoryId) this else null
        }

        override fun <T> executeRead(f: () -> T): T {
            return synchronized { f() }
        }

        override fun <T> executeWrite(f: () -> T): T {
            return synchronized { f() }
        }

        override fun canRead(): Boolean {
            return synchronized { this@LightModelClient.currentAccessType.canRead }
        }

        override fun canWrite(): Boolean {
            return synchronized { this@LightModelClient.currentAccessType.canWrite }
        }

        override fun addListener(l: IAreaListener) {
            TODO("Not yet implemented")
        }

        override fun removeListener(l: IAreaListener) {
            TODO("Not yet implemented")
        }
    }

    data class AreaReference(val branchId: String?) : IAreaReference

    companion object {
        private val LOG = mu.KotlinLogging.logger {}

        init {
            LightClientReferenceSerializer.register()
        }

        fun builder() = LightModelClientBuilder()
    }

    interface IConnection {
        fun sendMessage(message: MessageFromClient)
        fun connect(messageReceiver: (message: MessageFromServer)->Unit)
        fun disconnect()
    }

    private enum class AccessType(val canRead: Boolean, val canWrite: Boolean) {
        NONE(false, false),
        READ(true, false),
        WRITE(true, true);
    }
}

class LightModelClientBuilder {
    private var host: String = "localhost"
    private var port: Int = 48302
    private var url: String? = null
    private var connection: LightModelClient.IConnection? = null
    private var httpClient: HttpClient? = null
    private var httpEngine: HttpClientEngine? = null
    private var httpEngineFactory: HttpClientEngineFactory<*>? = null
    private var debugName: String = ""

    fun build(): LightModelClient {
        return LightModelClient(
            connection ?: (
                WebsocketConnection((httpClient ?: (
                        httpEngine?.let { HttpClient(it) } ?: httpEngineFactory?.let { HttpClient(it) } ?: HttpClient()
                    )
                ).config {
                    install(WebSockets)
                }, url ?: (
                    "ws://$host:$port/ws"
                ))
            ),
            debugName
        )
    }

    fun debugName(debugName: String): LightModelClientBuilder {
        this.debugName = debugName
        return this
    }
    fun host(host: String): LightModelClientBuilder {
        this.host = host
        return this
    }
    fun port(port: Int): LightModelClientBuilder {
        this.port = port
        return this
    }
    fun url(url: String): LightModelClientBuilder {
        this.url = url
        return this
    }
    fun connection(connection: LightModelClient.IConnection): LightModelClientBuilder {
        this.connection = connection
        return this
    }
    fun httpClient(httpClient: HttpClient): LightModelClientBuilder {
        this.httpClient = httpClient
        return this
    }
    fun httpEngine(httpEngine: HttpClientEngine): LightModelClientBuilder {
        this.httpEngine = httpEngine
        return this
    }
    fun httpEngine(httpEngineFactory: HttpClientEngineFactory<*>): LightModelClientBuilder {
        this.httpEngineFactory = httpEngineFactory
        return this
    }
}

object LightClientReferenceSerializer : INodeReferenceSerializer {
    fun register() {
        INodeReferenceSerializer.register(LightClientReferenceSerializer)
    }

    fun unregister() {
        INodeReferenceSerializer.unregister(LightClientReferenceSerializer)
    }

    override fun serialize(ref: INodeReference): String? {
        if (ref is LightModelClient.NodeAdapter) {
            throw UnsupportedOperationException("Don't use this reference type outside the " + LightModelClient::class.simpleName)
        }
        return null
    }

    override fun deserialize(serialized: String): INodeReference? {
        return null
    }
}

fun NodeData.asUpdateData(): NodeUpdateData {
    val isTempId = nodeId.startsWith(TEMP_ID_PREFIX)
    return NodeUpdateData(
        nodeId = if (isTempId) null else nodeId,
        temporaryNodeId = if (isTempId) nodeId else null,
        concept = concept,
        references = references,
        properties = properties,
        children = children
    )
}
