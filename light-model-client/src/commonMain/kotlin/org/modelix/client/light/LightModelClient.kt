package org.modelix.client.light

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.delay
import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IStateVariableGroup
import org.modelix.incremental.IStateVariableReference
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeEx
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.INodeReferenceSerializerEx
import org.modelix.model.api.NodeReferenceById
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.getAncestors
import org.modelix.model.api.resolve
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaChangeEvent
import org.modelix.model.area.IAreaChangeList
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import org.modelix.model.server.api.AddNewChildNodeOpData
import org.modelix.model.server.api.ChangeSetId
import org.modelix.model.server.api.DeleteNodeOpData
import org.modelix.model.server.api.ExceptionData
import org.modelix.model.server.api.MessageFromClient
import org.modelix.model.server.api.MessageFromServer
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.MoveNodeOpData
import org.modelix.model.server.api.NodeData
import org.modelix.model.server.api.NodeId
import org.modelix.model.server.api.NodeUpdateData
import org.modelix.model.server.api.OperationData
import org.modelix.model.server.api.QueryRootNode
import org.modelix.model.server.api.SetPropertyOpData
import org.modelix.model.server.api.SetReferenceOpData
import org.modelix.model.server.api.VersionData
import org.modelix.model.server.api.merge
import org.modelix.model.server.api.replaceChildren
import org.modelix.model.server.api.replaceContainment
import org.modelix.model.server.api.replaceId
import org.modelix.model.server.api.replaceReferences
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.untyped.ISupportsModelQL
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TEMP_ID_PREFIX = "tmp-"

class LightModelClient internal constructor(
    val connection: IConnection,
    private val transactionManager: ITransactionManager,
    val autoFilterNonLoadedNodes: Boolean,
    val debugName: String = "",
    val modelQLClient: ModelQLClient? = null,
) {

    private val nodes = NodesMap<NodeData>(this)
    private val area = Area()
    private var areaListeners: Set<IAreaListener> = emptySet()
    private var repositoryId: String? = null
    private var lastMergedVersionHash: String? = null
    private val pendingOperations: MutableList<OperationData> = ArrayList()
    private var rootNodeId: NodeId? = null
    private var usesRoleIds: Boolean = true
    private var temporaryIdsSequence: Long = 0
    private var changeSetIdSequence: Int = 0
    private val nodesReferencingTemporaryIds = HashSet<NodeId>()
    private val temporaryNodeAdapters = NodesMap<NodeAdapter>(this)
    private var initialized = false
    private var lastUnconfirmedChangeSetId: ChangeSetId? = null
    private val unappliedVersions: MutableList<VersionData> = ArrayList()
    private var exceptions: MutableList<ExceptionData> = ArrayList()
    private var currentModelQuery: ModelQuery? = null
    private var unappliedQuery: ModelQuery? = null

    init {
        connection.connect { message ->
            try {
                messageReceived(message)
            } catch (ex: Exception) {
                LOG.error(ex) { "Failed to process message: $message" }
            }
        }
        transactionManager.afterWrite {
            flush()
            val changes = object : IAreaChangeList {
                override fun visitChanges(visitor: (IAreaChangeEvent) -> Boolean) {}
            }
            areaListeners.forEach { l ->
                try {
                    l.areaChanged(changes)
                } catch (ex: Exception) {
                    LOG.error(ex) { "" }
                }
            }
        }
    }

    fun dispose() {
        connection.disconnect()
    }

    fun changeQuery(query: ModelQuery) {
        // TODO get rid of node data that is not selected by the query anymore
        runWrite {
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
        if (!transactionManager.canRead()) throw IllegalStateException("Not in a read transaction")
    }

    private fun checkWrite() {
        if (!transactionManager.canWrite()) throw IllegalStateException("Not in a write transaction")
    }

    fun isInSync(): Boolean = runRead {
        checkException()
        lastUnconfirmedChangeSetId == null && pendingOperations.isEmpty() && unappliedVersions.isEmpty()
    }

    private fun <T> requiresRead(body: () -> T): T {
        return transactionManager.requiresRead(body)
    }

    private fun <T> requiresWrite(body: () -> T): T {
        return transactionManager.requiresWrite(body)
    }

    fun <T> runRead(body: () -> T): T {
        return transactionManager.runRead(body)
    }

    fun <T> runWrite(body: () -> T): T {
        return transactionManager.runWrite(body)
    }

    suspend fun waitForRootNode(timeout: Duration = 30.seconds): INode? {
        val query = currentModelQuery ?: unappliedQuery
        if (query != null && query.queries.filterIsInstance<QueryRootNode>().isEmpty()) {
            throw IllegalStateException("The root node is not included in the model query: ${query.toJson()}")
        }

        var result: INode? = null
        kotlinx.coroutines.withTimeout(timeout) {
            while (result == null) {
                checkException()
                runRead {
                    val node = getRootNode()
                    if (node != null && node.isValid) {
                        result = node
                    }
                }
                if (result != null) break
                delay(10.milliseconds)
            }
        }
        return result
    }

    fun getRepositoryId(): String? = repositoryId

    fun getRootNode(): INode? {
        return requiresRead {
            rootNodeId?.let { getNodeAdapter(it) }
        }
    }

    fun getNodeIfLoaded(nodeId: NodeId): INode? {
        return requiresRead {
            if (nodes.containsKey(nodeId)) getNodeAdapter(nodeId) else null
        }
    }

    fun tryGetParentId(nodeId: NodeId): NodeId? {
        return requiresRead { nodes[nodeId]?.parent }
    }

    fun isInitialized(): Boolean = runRead { initialized }

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

    fun getNode(nodeId: NodeId): NodeAdapter {
        return requiresRead {
            getNodeData(nodeId) // fail fast if it doesn't exist
            return@requiresRead getNodeAdapter(nodeId)
        }
    }

    private fun generateTemporaryNodeId(): String = requiresRead {
        TEMP_ID_PREFIX + (++temporaryIdsSequence).toString(16)
    }

    private fun flush() {
        if (pendingOperations.isNotEmpty()) {
            val changeSetId: ChangeSetId = ++changeSetIdSequence
            lastUnconfirmedChangeSetId = changeSetId
            val message = MessageFromClient(
                operations = ArrayList(pendingOperations),
                changeSetId = changeSetId,
                baseVersionHash = lastMergedVersionHash,
                baseChangeSet = lastUnconfirmedChangeSetId,
            )
            // println("message to server: " + message.toJson())
            connection.sendMessage(message)
            pendingOperations.clear()
        }
    }

    private fun messageReceived(message: MessageFromServer) {
        runWrite {
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
                currentModelQuery = unappliedQuery
                unappliedQuery = null
            }
            fullConsistencyCheck()
        }
    }

    private fun applyVersion(version: VersionData) {
        runWrite {
            initialized = true
            if (version.repositoryId != null && version.repositoryId != repositoryId) {
                repositoryId = version.repositoryId
            }
            if (version.rootNodeId != null && version.rootNodeId != rootNodeId) {
                rootNodeId = version.rootNodeId
            }
            usesRoleIds = version.usesRoleIds
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
        // println("Removed $nodeId: $data")
    }

    private fun replaceIds(replacements: Map<String, String>) {
        runWrite {
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
                if (newData.references.values.all { !it.startsWith(TEMP_ID_PREFIX) } &&
                    newData.children.values.flatten().all { !it.startsWith(TEMP_ID_PREFIX) } &&
                    newData.parent?.startsWith(TEMP_ID_PREFIX) != true
                ) {
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
        return requiresRead {
            nodes[id]
                ?: throw IllegalArgumentException("Node with ID $id doesn't exist or wasn't loaded.")
        }
    }

    private fun getNodeAdapter(nodeId: NodeId): NodeAdapter {
        return requiresRead {
            if (nodeId.startsWith(TEMP_ID_PREFIX)) {
                temporaryNodeAdapters.getOrPut(nodeId) { NodeAdapter(nodeId) }
            } else {
                NodeAdapter(nodeId)
            }
        }
    }

    inner class NodeAdapter(var nodeId: NodeId) : INodeEx, ISupportsModelQL {
        fun getData() = requiresRead { getNodeData(nodeId) }

        override fun usesRoleIds(): Boolean = usesRoleIds

        override fun getArea(): IArea = area

        override val isValid: Boolean
            get() = requiresRead { nodes.containsKey(nodeId) }
        override val reference: INodeReference
            get() = LightClientNodeReference(nodeId)
        override val concept: IConcept?
            get() = requiresRead { getConceptReference()?.resolve() }
        override val roleInParent: String?
            get() = requiresRead { getData().role }
        override val parent: NodeAdapter?
            get() = requiresRead { getData().parent?.let { getNodeAdapter(it) } }

        override fun getChildren(role: String?): List<NodeAdapter> {
            return requiresRead {
                val children = getData().children[role]?.map { getNodeAdapter(it) } ?: emptyList()
                if (autoFilterNonLoadedNodes) children.filterLoaded() else children
            }
        }

        override val allChildren: List<NodeAdapter>
            get() = requiresRead {
                val children = getData().children.flatMap { it.value }.map { getNodeAdapter(it) }
                if (autoFilterNonLoadedNodes) children.filterLoaded() else children
            }

        override fun getConceptReference(): IConceptReference? {
            return requiresRead { getData().concept?.let { ConceptReference(it) } }
        }

        override fun moveChild(role: String?, index: Int, child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Not part of this client: $child" }
            requiresWrite {
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
            return requiresWrite {
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
                return@requiresWrite childNode
            }
        }

        override fun removeChild(child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Unsupported child type: $child" }
            requiresWrite {
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
                                // println("removing reference $sourceNodeId.$brokenRole -> $childId")
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
                // pendingUpdates.removeAll { it.nodeId == childId }
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
            return getReferenceTargetRef(role)?.let { getNode(it.nodeId) }
                ?.takeIf { !autoFilterNonLoadedNodes || it.isLoaded() }
        }

        override fun getReferenceTargetRef(role: String): LightClientNodeReference? {
            return requiresRead { getData().references[role]?.let { LightClientNodeReference(it) } }
        }

        override fun setReferenceTarget(role: String, target: INodeReference?) {
            requiresWrite {
                val targetId: NodeId? = when (target) {
                    null -> null
                    is LightClientNodeReference -> target.nodeId
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
            requiresWrite { setReferenceTarget(role, target?.reference) }
        }

        override fun getPropertyValue(role: String): String? {
            return getData().properties[role]
        }

        override fun setPropertyValue(propertyRole: String, value: String?) {
            requiresWrite {
                val oldData = getData()
                nodes[nodeId] = with(oldData) {
                    NodeData(
                        nodeId = nodeId,
                        concept = concept,
                        parent = parent,
                        role = role,
                        properties = if (value == null) properties - propertyRole else properties + (propertyRole to value),
                        references = references,
                        children = children,
                    )
                }
                pendingOperations.add(SetPropertyOpData(nodeId, propertyRole, value))
            }
        }

        override fun getPropertyRoles(): List<String> {
            return requiresRead { getData().properties.keys.toList() }
        }

        override fun getReferenceRoles(): List<String> {
            return requiresRead { getData().references.keys.toList() }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NodeAdapter

            val sameNodeId = runRead { nodeId == other.nodeId }
            if (!sameNodeId) return false
            if (getClient() != other.getClient()) return false

            return true
        }

        override fun hashCode(): Int {
            if (nodeId.startsWith(TEMP_ID_PREFIX)) {
                throw IllegalStateException(
                    "The server hasn't yet assigned an ID to this node." +
                        " The ID and the hashCode will change.",
                )
            }
            return nodeId.hashCode()
        }

        fun getClient(): LightModelClient = this@LightModelClient

        override fun toString(): String {
            return nodeId
        }

        override fun createQueryExecutor(): IQueryExecutor<INode> {
            val client = modelQLClient ?: throw UnsupportedOperationException("Connection doesn't support ModelQL: $connection")
            return client.getNode(SerializedNodeReference(nodeId))
        }
    }

    inner class Area : IArea {
        fun getClient(): LightModelClient = this@LightModelClient

        override fun getRoot(): INode {
            return requiresRead { rootNodeId?.let { getNodeAdapter(it) } ?: throw IllegalStateException("Root node ID unknown") }
        }

        @Deprecated("use ILanguageRepository.resolveConcept")
        override fun resolveConcept(ref: IConceptReference): IConcept? {
            TODO("Not yet implemented")
        }

        override fun resolveNode(ref: INodeReference): INode? {
            return requiresRead { resolveOriginalNode(ref) }
        }

        override fun resolveOriginalNode(ref: INodeReference): INode? {
            return requiresRead {
                when (ref) {
                    is LightClientNodeReference -> getNode(ref.nodeId)
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
            return runRead { f() }
        }

        override fun <T> executeWrite(f: () -> T): T {
            return runWrite { f() }
        }

        override fun canRead(): Boolean {
            return transactionManager.canRead()
        }

        override fun canWrite(): Boolean {
            return transactionManager.canWrite()
        }

        override fun addListener(l: IAreaListener) {
            areaListeners += l
        }

        override fun removeListener(l: IAreaListener) {
            areaListeners -= l
        }
    }

    data class AreaReference(val branchId: String?) : IAreaReference

    companion object {
        private val LOG = mu.KotlinLogging.logger {}

        init {
            LightClientReferenceSerializer.register()
        }

        @JvmStatic
        fun builder(): LightModelClientBuilder = PlatformSpecificLightModelClientBuilder()
    }

    interface IConnection {
        fun sendMessage(message: MessageFromClient)
        fun connect(messageReceiver: (message: MessageFromServer) -> Unit)
        fun disconnect()
    }

    private enum class AccessType(val canRead: Boolean, val canWrite: Boolean) {
        NONE(false, false),
        READ(true, false),
        WRITE(true, true),
    }
}

internal interface ITransactionManager {
    fun <T> requiresRead(body: () -> T): T
    fun <T> requiresWrite(body: () -> T): T
    fun <T> runRead(body: () -> T): T
    fun <T> runWrite(body: () -> T): T
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun afterWrite(listener: () -> Unit)
}

private class ReadWriteLockTransactionManager : ITransactionManager {
    private var writeListener: (() -> Unit)? = null
    private val lock = ReadWriteLock()
    private var writeLevel = 0
    override fun <T> requiresRead(body: () -> T): T {
        if (!lock.canRead()) throw IllegalStateException("Not in a read transaction")
        return body()
    }
    override fun <T> requiresWrite(body: () -> T): T {
        if (!lock.canWrite()) throw IllegalStateException("Not in a write transaction")
        return body()
    }
    override fun <T> runRead(body: () -> T): T = lock.runRead(body)
    override fun <T> runWrite(body: () -> T): T {
        return lock.runWrite {
            writeLevel++
            try {
                body()
            } finally {
                writeLevel--
                if (writeLevel == 0) {
                    try {
                        writeListener?.invoke()
                    } catch (ex: Exception) {
                        mu.KotlinLogging.logger { }.error(ex) { "Exception in write listener" }
                    }
                }
            }
        }
    }
    override fun canRead(): Boolean = lock.canRead()
    override fun canWrite(): Boolean = lock.canWrite()
    override fun afterWrite(listener: () -> Unit) {
        if (writeListener != null) throw IllegalStateException("Only one listener is supported")
        writeListener = listener
    }
}

private class AutoTransactions(val delegate: ITransactionManager) : ITransactionManager by delegate {
    override fun <T> requiresRead(body: () -> T): T {
        return if (canRead()) {
            body()
        } else {
            runRead(body)
        }
    }

    override fun <T> requiresWrite(body: () -> T): T {
        return if (canWrite()) {
            body()
        } else {
            runWrite(body)
        }
    }
}

expect class PlatformSpecificLightModelClientBuilder() : LightModelClientBuilder

abstract class LightModelClientBuilder {
    private var host: String = "localhost"
    private var port: Int = 48302
    private var url: String? = null
    private var connection: LightModelClient.IConnection? = null
    private var httpClient: HttpClient? = null
    private var httpEngine: HttpClientEngine? = null
    private var httpEngineFactory: HttpClientEngineFactory<*>? = null
    private var debugName: String = ""
    private var transactionManager: ITransactionManager = ReadWriteLockTransactionManager()
    private var autoFilterNonLoadedNodes: Boolean = false
    private var modelQLClient: ModelQLClient? = null

    protected abstract fun getDefaultEngineFactory(): HttpClientEngineFactory<*>

    fun build(): LightModelClient {
        return LightModelClient(
            connection ?: (
                WebsocketConnection(
                    (
                        httpClient ?: (
                            httpEngine?.let { HttpClient(it) } ?: (httpEngineFactory ?: getDefaultEngineFactory()).let { HttpClient(it) }
                            )
                        ).config {
                        install(WebSockets)
                    },
                    url ?: (
                        "ws://$host:$port/ws"
                        ),
                )
                ),
            transactionManager,
            autoFilterNonLoadedNodes = autoFilterNonLoadedNodes,
            debugName = debugName,
            modelQLClient = modelQLClient,
        )
    }
    fun autoFilterNonLoadedNodes(value: Boolean = true): LightModelClientBuilder {
        autoFilterNonLoadedNodes = value
        return this
    }
    fun autoTransactions(): LightModelClientBuilder {
        return transactionManager(AutoTransactions(transactionManager))
    }
    private fun transactionManager(tm: ITransactionManager): LightModelClientBuilder {
        this.transactionManager = tm
        return this
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
    fun modelQLClient(c: ModelQLClient): LightModelClientBuilder {
        this.modelQLClient = c
        return this
    }
}

class LightClientNodeReference(val nodeId: NodeId) : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        return when (area) {
            is LightModelClient.Area -> area.getClient().getNode(nodeId)
            else -> null
        }
    }
}

object LightClientReferenceSerializer : INodeReferenceSerializerEx {
    override val prefix: String = "light-client"
    override val supportedReferenceClasses = setOf(LightClientNodeReference::class)

    fun register() {
        INodeReferenceSerializer.register(LightClientReferenceSerializer)
    }

    fun unregister() {
        INodeReferenceSerializer.unregister(LightClientReferenceSerializer)
    }

    override fun serialize(ref: INodeReference): String {
        return (ref as LightClientNodeReference).nodeId
    }

    override fun deserialize(serialized: String): INodeReference {
        return LightClientNodeReference(serialized)
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
        children = children,
    )
}

fun INode.isLoaded() = isValid
fun <T : INode> Iterable<T>.filterLoaded() = filter { it.isLoaded() }
fun <T : INode> Sequence<T>.filterLoaded() = filter { it.isLoaded() }

private data class ClientDependency(val client: LightModelClient) : IStateVariableGroup {
    override fun getGroup(): IStateVariableGroup? {
        return null
    }
}

private data class NodeDataDependency(val client: LightModelClient, val id: NodeId) : IStateVariableReference<NodeData> {
    override fun getGroup(): IStateVariableGroup {
        return client.tryGetParentId(id)
            ?.let { NodeDataDependency(client, it) }
            ?: ClientDependency(client)
    }

    override fun read(): NodeData {
        return client.getNode(id).getData()
    }
}

private class NodesMap<V : Any>(val client: LightModelClient) {
    private val map: MutableMap<NodeId, V> = HashMap()

    operator fun get(key: NodeId): V? {
        DependencyTracking.accessed(NodeDataDependency(client, key))
        return map[key]
    }

    operator fun set(key: NodeId, value: V) {
        if (map[key] == value) return
        map[key] = value
        DependencyTracking.modified(NodeDataDependency(client, key))
    }

    fun remove(key: NodeId): V? {
        if (!map.containsKey(key)) return null
        val result = map.remove(key)
        DependencyTracking.modified(NodeDataDependency(client, key))
        return result
    }

    fun clear() {
        if (map.isEmpty()) return
        val removedKeys = map.keys.toList()
        map.clear()
        for (key in removedKeys) {
            DependencyTracking.modified(NodeDataDependency(client, key))
        }
    }

    fun containsKey(key: NodeId): Boolean {
        DependencyTracking.accessed(NodeDataDependency(client, key))
        return map.containsKey(key)
    }

    fun getOrPut(key: NodeId, defaultValue: () -> V): V {
        DependencyTracking.accessed(NodeDataDependency(client, key))
        map[key]?.let { return it }
        val createdValue = defaultValue()
        map[key] = createdValue
        // No modified notification necessary, because only the first access modifies the map, but then there can't be
        // any dependency on that key yet.
        // DependencyTracking.modified(NodeDataDependency(client, key))
        return createdValue
    }
}
