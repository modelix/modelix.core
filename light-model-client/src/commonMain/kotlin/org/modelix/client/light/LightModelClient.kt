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
    private val pendingUpdates: MutableList<NodeUpdateData> = ArrayList()
    private var rootNodeId: NodeId? = null
    private var temporaryIdsSequence: Long = 0
    private val nodesReferencingTemporaryIds = HashSet<NodeId>()
    private var synchronizationLevel: Int = 0

    init {
        connection.receiveMessages { message ->
            try {
                messageReceived(message)
            } catch (ex: Exception) {
                LOG.error(ex) { "Failed to process message: $message" }
            }
        }
    }

    fun getRepositoryId(): String? = repositoryId

    fun getRootNode(): INode? {
        return rootNodeId?.let { NodeAdapter(it) }
    }

    fun getNode(nodeId: NodeId): INode = NodeAdapter(nodeId)

    private fun generateTemporaryNodeId(): String = TEMP_ID_PREFIX + (++temporaryIdsSequence).toString(16)
    
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

    fun flush() {
        if (pendingUpdates.isNotEmpty()) {
            connection.sendMessage(MessageFromClient(ArrayList(pendingUpdates)))
            pendingUpdates.clear()
        }
    }

    private fun messageReceived(message: MessageFromServer) {
        synchronized {
            message.replacedIds?.let { replaceIds(it) }
            message.version?.let { versionReceived(it) }
        }
    }

    private fun versionReceived(version: VersionData) {
        synchronized {
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
                }
                nodes[sourceId] = newData
                if (newData.references.values.all { !it.startsWith(TEMP_ID_PREFIX) }) {
                    noTempReferencesLeft.add(sourceId)
                }
            }
            nodesReferencingTemporaryIds.removeAll(noTempReferencesLeft)
            for (replacement in replacements) {
                val oldData: NodeData = nodes[replacement.key] ?: continue
                nodes[replacement.value] = oldData.replaceId(replacement.value)
            }
        }
    }

    private fun getNodeData(id: NodeId): NodeData {
        return synchronized {
            nodes[id] ?: throw IllegalArgumentException("Node with ID $id doesn't exist")
        }
    }

    inner class NodeAdapter(val nodeId: NodeId) : INode, INodeReference {
        fun getData() = getNodeData(nodeId)

        override fun resolveNode(area: IArea?): INode? = this

        override fun getArea(): IArea = area

        override val isValid: Boolean
            get() = synchronized { nodes.containsKey(nodeId) }
        override val reference: INodeReference
            get() = NodeReferenceById(nodeId)
        override val concept: IConcept?
            get() = getConceptReference()?.resolve()
        override val roleInParent: String?
            get() = getData().role
        override val parent: INode?
            get() = getData().parent?.let { NodeAdapter(nodeId) }

        override fun getChildren(role: String?): Iterable<INode> = allChildren.filter { it.roleInParent == role }

        override val allChildren: Iterable<INode>
            get() = getData().children.map { NodeAdapter(it) }

        override fun getConceptReference(): IConceptReference? {
            return getData().concept?.let { ConceptReference(it) }
        }

        override fun moveChild(role: String?, index: Int, child: INode) {
            TODO("Not yet implemented")
        }

        override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
            TODO("Not yet implemented")
        }

        override fun removeChild(child: INode) {
            TODO("Not yet implemented")
        }

        override fun getReferenceTarget(role: String): INode? {
            return getReferenceTargetRef(role)?.resolveNode(getArea())
        }

        override fun getReferenceTargetRef(role: String): INodeReference? {
            return getData().references[role]?.let { INodeReferenceSerializer.deserialize(it) }
        }

        override fun setReferenceTarget(role: String, target: INodeReference?) {
            require(target == null || (target is NodeAdapter && target.getClient() == getClient())) { "Only local references are supported" }
            val target = target as NodeAdapter?
            synchronized {
                nodes[nodeId] = getData().replaceReferences {
                    if (target == null) it - role else it + (role to target.nodeId)
                }
                pendingUpdates.add(NodeUpdateData(nodeId = nodeId, references = mapOf(role to target?.nodeId)))
            }
        }

        override fun setReferenceTarget(role: String, target: INode?) {
            setReferenceTarget(role, target?.reference)
        }

        override fun getPropertyValue(role: String): String? {
            return getData().properties[role]
        }

        override fun setPropertyValue(role: String, value: String?) {
            synchronized {
                val oldData = getData()
                nodes[nodeId] = with(oldData) {
                    NodeData(
                        nodeId = nodeId,
                        concept = concept,
                        parent = parent,
                        role = role,
                        properties = if (value == null) properties - role else properties + (role to value),
                        references = references,
                        children = children
                    )
                }
                pendingUpdates.add(NodeUpdateData(nodeId = nodeId, properties = mapOf(role to value)))
            }
        }

        override fun getPropertyRoles(): List<String> {
            return getData().properties.keys.toList()
        }

        override fun getReferenceRoles(): List<String> {
            return getData().references.keys.toList()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as NodeAdapter

            if (nodeId != other.nodeId) return false
            if (getClient() != other.getClient()) return false

            return true
        }

        override fun hashCode(): Int {
            return nodeId.hashCode()
        }

        fun getClient(): LightModelClient = this@LightModelClient
    }

    inner class Area : IArea {
        override fun getRoot(): INode {
            return rootNodeId?.let { NodeAdapter(it) } ?: throw IllegalStateException("Root node ID unknown")
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
        fun receiveMessages(listener: (message: MessageFromServer)->Unit)
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