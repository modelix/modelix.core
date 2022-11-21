package org.modelix.client.light

import org.modelix.model.api.*
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import org.modelix.model.server.api.*

private const val TEMP_ID_PREFIX = "tmp-"

class LightModelClient(val connection: IConnection) {

    private val nodes: MutableMap<NodeId, NodeData> = HashMap()
    private val area = Area()
    private var repositoryId: String? = null
    private var versionHash: String? = null
    private val pendingUpdates: MutableMap<NodeId, NodeUpdateData> = LinkedHashMap()
    private var rootNodeId: NodeId? = null
    private var temporaryIdsSequence: Long = 0
    private var changeSetIdSequence: Int = 0
    private val nodesReferencingTemporaryIds = HashSet<NodeId>()
    private var synchronizationLevel: Int = 0
    private val temporaryNodeAdapters: MutableMap<String, NodeAdapter> = HashMap()
    private var initialized = false
    private val unconfirmedChangeSets: MutableSet<ChangeSetId> = LinkedHashSet()
    private val unappliedVersions: MutableList<VersionData> = ArrayList()
    private var exceptions: MutableList<ExceptionData> = ArrayList()

    init {
        connection.connect { message ->
            try {
                messageReceived(message)
            } catch (ex: Exception) {
                LOG.error(ex) { "Failed to process message: $message" }
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

    fun <T> runRead(body: () -> T): T = synchronized { body() }
    fun <T> runWrite(body: () -> T): T = synchronized { body() }

    fun getRepositoryId(): String? = repositoryId

    fun getRootNode(): INode? {
        return rootNodeId?.let { getNodeAdapter(it) }
    }

    fun isInitialized(): Boolean = initialized

    fun hasTemporaryIds(): Boolean = synchronized {
        temporaryNodeAdapters.isNotEmpty() || nodesReferencingTemporaryIds.isNotEmpty()
    }

    fun getNode(nodeId: NodeId): NodeAdapter {
        return synchronized {
            getNodeData(nodeId) // fail fast if it doesn't exist
            return@synchronized getNodeAdapter(nodeId)
        }
    }

    private fun appendUpdate(nodeId: NodeId, body: (NodeUpdateData)->NodeUpdateData) {
        synchronized {
            val existing = pendingUpdates[nodeId]
                ?: if (nodeId.startsWith(TEMP_ID_PREFIX)) {
                    NodeUpdateData(nodeId = null, temporaryNodeId = nodeId)
                } else {
                    NodeUpdateData.nothing(nodeId)
                }
            pendingUpdates[nodeId] = body(existing)
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
        if (pendingUpdates.isNotEmpty()) {
            val changeSetId: ChangeSetId = ++changeSetIdSequence
            val message = MessageFromClient(
                changedNodes = pendingUpdates.values.toList(),
                changeSetId = changeSetId
            )
            connection.sendMessage(message)
            unconfirmedChangeSets.add(changeSetId)
            pendingUpdates.clear()
        }
    }

    private fun messageReceived(message: MessageFromServer) {
        synchronized {
            if (message.exception != null) {
                exceptions.add(message.exception!!)
            }
            message.replacedIds?.let { replaceIds(it) }
            unconfirmedChangeSets.removeAll(message.includedChangeSets)
            message.version?.let { unappliedVersions.add(it) }
            if (unconfirmedChangeSets.isEmpty()) {
                unappliedVersions.forEach { applyVersion(it) }
                unappliedVersions.clear()
            }
        }
    }

    private fun applyVersion(version: VersionData) {
        synchronized {
            println("apply ${version.versionHash}")
            initialized = true
            if (version.repositoryId != null && version.repositoryId != repositoryId) {
                repositoryId = version.repositoryId
            }
            if (version.rootNodeId != null && version.rootNodeId != rootNodeId) {
                rootNodeId = version.rootNodeId
            }
            versionHash = version.versionHash
            for (nodeData in version.nodes) {
                nodes[nodeData.nodeId] = nodeData
            }
        }
    }

    private fun replaceIds(replacements: Map<String, String>) {
        synchronized {
            val noTempReferencesLeft = ArrayList<String>(nodesReferencingTemporaryIds.size)
            for (sourceId in nodesReferencingTemporaryIds) {
                val oldData = nodes[sourceId] ?: continue
                val newData = oldData.replaceReferences { references ->
                    references.map { it.key to (replacements[it.value] ?: it.value) }.toMap()
                }.replaceChildren { children ->
                    children.mapValues { role2list -> role2list.value.map { child -> replacements[child] ?: child } }
                }
                nodes[sourceId] = newData
                if (newData.references.values.all { !it.startsWith(TEMP_ID_PREFIX) }
                    && newData.children.values.flatten().all { !it.startsWith(TEMP_ID_PREFIX) }) {
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
            pendingUpdates.putAll(pendingUpdates.mapValues { it.value.replaceIds { id -> replacements[id] } })
        }
    }

    private fun getNodeData(id: NodeId): NodeData {
        return synchronized {
            nodes[id]
                ?: throw IllegalArgumentException("Node with ID $id doesn't exist")
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
        fun getData() = synchronized { getNodeData(nodeId) }

        override fun resolveNode(area: IArea?): INode? = this

        override fun getArea(): IArea = area

        override val isValid: Boolean
            get() = synchronized { nodes.containsKey(nodeId) }
        override val reference: INodeReference
            get() = this
        override val concept: IConcept?
            get() = getConceptReference()?.resolve()
        override val roleInParent: String?
            get() = getData().role
        override val parent: INode?
            get() = synchronized { getData().parent?.let { getNodeAdapter(it) } }

        override fun getChildren(role: String?): List<INode> = synchronized { allChildren.filter { it.roleInParent == role } }

        override val allChildren: List<INode>
            get() = synchronized { getData().children.flatMap { it.value }.map { getNodeAdapter(it) } }

        override fun getConceptReference(): IConceptReference? {
            return synchronized { getData().concept?.let { ConceptReference(it) } }
        }

        override fun moveChild(role: String?, index: Int, child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Not part of this client: $child" }
            synchronized {
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
                if (!sameParent) oldParent.appendUpdate { it.withChildren(oldRole, oldParent.getData().children[oldRole]) }
                appendUpdate { it.withChildren(role, getData().children[role]) }
            }
        }

        override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
            return addNewChild(role, index, concept?.getReference())
        }

        override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
            return synchronized {
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
                appendUpdate { it.withChildren(role, newParentData.children[role]) }
                appendUpdate(childId) { it.withConcept(concept) }
                return@synchronized getNodeAdapter(childId)
            }
        }

        override fun removeChild(child: INode) {
            require(child is NodeAdapter && child.getClient() == getClient()) { "Unsupported child type: $child" }
            synchronized {
                require(child.parent == this) { "$child is not a child of $this" }
                require(child.allChildren.isEmpty()) { "$child is not empty" }

                val childId = child.nodeId

                // remove broken references
                if (childId.startsWith(TEMP_ID_PREFIX)) {
                    for (sourceNodeId in nodesReferencingTemporaryIds) {
                        val brokenRoles = getNodeData(sourceNodeId).references.filter { it.value == childId }.map { it.key }
                        if (brokenRoles.isNotEmpty()) {
                            val sourceNode = getNode(sourceNodeId)
                            brokenRoles.forEach { brokenRole -> sourceNode.setReferenceTarget(brokenRole, null as INodeReference?) }
                        }
                    }
                }

                val oldParentData = getData()
                val role = child.roleInParent
                val newParentData = oldParentData.replaceChildren(role) { it - childId }
                nodes[nodeId] = newParentData
                appendUpdate { it.withChildren(role, newParentData.children[role]!!) }
                nodes.remove(childId)
                pendingUpdates.remove(childId)
                nodesReferencingTemporaryIds.remove(childId)
            }
        }

        override fun getReferenceTarget(role: String): NodeAdapter? {
            return getReferenceTargetRef(role)
        }

        override fun getReferenceTargetRef(role: String): NodeAdapter? {
            return synchronized { getData().references[role]?.let { NodeAdapter(it) } }
        }

        override fun setReferenceTarget(role: String, target: INodeReference?) {
            synchronized {
                val targetId: NodeId? = when (target) {
                    null -> null
                    is NodeAdapter -> target.nodeId
                    is NodeReferenceById -> target.nodeId
                    else -> throw IllegalArgumentException("Unsupported reference: $target")
                }
                nodes[nodeId] = getData().replaceReferences {
                    if (targetId == null) it - role else it + (role to targetId)
                }
                appendUpdate { it.withReference(role, targetId) }
            }
        }

        private fun appendUpdate(body: (NodeUpdateData)->NodeUpdateData) = synchronized { appendUpdate(nodeId, body) }

        override fun setReferenceTarget(role: String, target: INode?) {
            setReferenceTarget(role, target?.reference)
        }

        override fun getPropertyValue(role: String): String? {
            return getData().properties[role]
        }

        override fun setPropertyValue(propertyRole: String, value: String?) {
            synchronized {
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
                appendUpdate { it.withProperty(propertyRole, value) }
            }
        }

        override fun getPropertyRoles(): List<String> {
            return synchronized { getData().properties.keys.toList() }
        }

        override fun getReferenceRoles(): List<String> {
            return synchronized { getData().references.keys.toList() }
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
            return rootNodeId?.let { getNodeAdapter(it) } ?: throw IllegalStateException("Root node ID unknown")
        }

        @Deprecated("use ILanguageRepository.resolveConcept")
        override fun resolveConcept(ref: IConceptReference): IConcept? {
            TODO("Not yet implemented")
        }

        override fun resolveNode(ref: INodeReference): INode? {
            return resolveNode(ref)
        }

        override fun resolveOriginalNode(ref: INodeReference): INode? {
            return when (ref) {
                is NodeAdapter -> ref
                else -> null
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
            return true
        }

        override fun canWrite(): Boolean {
            return true
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
            INodeReferenceSerializer.register(LightClientReferenceSerializer)
        }
    }

    interface IConnection {
        fun sendMessage(message: MessageFromClient)
        fun connect(messageReceiver: (message: MessageFromServer)->Unit)
    }
}

private object LightClientReferenceSerializer : INodeReferenceSerializer {
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

private fun NodeData.replaceReferences(f: (Map<String, String>)->Map<String, String>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = f(references),
        children = children
    )
}

private fun NodeData.replaceChildren(f: (Map<String?, List<String>>)->Map<String?, List<String>>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = f(children)
    )
}

private fun NodeData.replaceChildren(role: String?, f: (List<String>)->List<String>): NodeData {
    return replaceChildren { allOldChildren ->
        val oldChildren = (allOldChildren[role] ?: emptyList()).toMutableList()
        val newChildren = f(oldChildren)
        allOldChildren + (role to newChildren)
    }
}

private fun NodeData.replaceContainment(newParent: NodeId, newRole: String?): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = newParent,
        role = newRole,
        properties = properties,
        references = references,
        children = children
    )
}

private fun NodeData.replaceId(newId: String): NodeData {
    return NodeData(
        nodeId = newId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = children
    )
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