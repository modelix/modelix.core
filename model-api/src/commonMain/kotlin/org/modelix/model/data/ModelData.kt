package org.modelix.model.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.PNodeReference

@Serializable
data class ModelData(
    val id: String? = null,
    val root: NodeData,
) {
    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    /**
     * The [idStrategy] can be provided if nodes should be created with a specific ID.
     * [setOriginalIdProperty] whether to safe the original ID in [NodeData.ID_PROPERTY_KEY].
     */
    fun load(
        branch: IBranch,
        idStrategy: ((NodeData) -> Long)? = null,
        setOriginalIdProperty: Boolean = true,
    ) {
        branch.computeWriteT { t ->
            val createdNodes = HashMap<String, Long>()
            val pendingReferences = ArrayList<() -> Unit>()
            val parentId = ITree.ROOT_ID
            if (root.id != null) {
                createdNodes[root.id] = parentId
            }
            if (setOriginalIdProperty) {
                setOriginalId(root, t, parentId)
            }
            for (nodeData in root.children) {
                loadNode(nodeData, t, parentId, createdNodes, pendingReferences, idStrategy, setOriginalIdProperty)
            }
            pendingReferences.forEach { it() }
        }
    }

    private fun loadNode(
        nodeData: NodeData,
        t: IWriteTransaction,
        parentId: Long,
        createdNodes: HashMap<String, Long>,
        pendingReferences: ArrayList<() -> Unit>,
        idStrategy: ((NodeData) -> Long)?,
        setOriginalIdProperty: Boolean,
    ) {
        val conceptRef = nodeData.concept?.let { ConceptReference(it) }
        val createdId = if (idStrategy == null) {
            t.addNewChild(parentId, nodeData.role, -1, conceptRef)
        } else {
            val id = idStrategy(nodeData)
            t.addNewChild(parentId, nodeData.role, -1, id, conceptRef)
            id
        }
        if (nodeData.id != null) {
            createdNodes[nodeData.id] = createdId
            if (setOriginalIdProperty) {
                setOriginalId(nodeData, t, createdId)
            }
        }
        for (propertyData in nodeData.properties) {
            t.setProperty(createdId, propertyData.key, propertyData.value)
        }
        for (referenceData in nodeData.references) {
            pendingReferences += {
                val target = createdNodes[referenceData.value]?.let { PNodeReference(it, t.tree.getId()) }
                t.setReferenceTarget(createdId, referenceData.key, target)
            }
        }
        for (childData in nodeData.children) {
            loadNode(childData, t, createdId, createdNodes, pendingReferences, idStrategy, setOriginalIdProperty)
        }
    }

    private fun setOriginalId(
        nodeData: NodeData,
        t: IWriteTransaction,
        nodeId: Long,
    ) {
        val key = NodeData.idPropertyKey
        t.setProperty(nodeId, key, nodeData.properties[key] ?: nodeData.id)
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true }
        fun fromJson(serialized: String): ModelData = Json.decodeFromString(serialized)
    }
}

@Serializable
data class NodeData(
    val id: String? = null,
    val concept: String? = null,
    val role: String? = null,
    val children: List<NodeData> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
) {

    fun normalize(): NodeData = copy(
        children = children.map { it.normalize() },
        properties = properties.entries.sortedBy { it.key }.associate { it.key to it.value },
        references = references.entries.sortedBy { it.key }.associate { it.key to it.value },
    )

    fun toJson() = prettyJson.encodeToString(this)

    companion object {
        private val prettyJson = Json { prettyPrint = true }

        /**
         * Users should not use this directly. Use [INode.getOriginalReference].
         */
        const val ID_PROPERTY_KEY = "#originalRef#"

        val ID_PROPERTY_REF = IPropertyReference.fromIdAndName(ID_PROPERTY_KEY, ID_PROPERTY_KEY)

        @Deprecated("Use ID_PROPERTY_KEY", replaceWith = ReplaceWith("ID_PROPERTY_KEY"))
        const val idPropertyKey = ID_PROPERTY_KEY
    }
}

fun NodeData.uid(model: ModelData): String {
    return (model.id ?: throw IllegalArgumentException("Model has no ID")) +
        "/" +
        (id ?: throw IllegalArgumentException("Node has no ID"))
}
fun ModelData.nodeUID(node: NodeData): String = node.uid(this)

fun INode.asData(): NodeData = NodeData(
    id = reference.serialize(),
    concept = getConceptReference()?.getUID(),
    role = getContainmentLink()?.toReference()?.getIdOrNameOrNull(),
    properties = getPropertyLinks().associateWithNotNull { getPropertyValue(it) }
        .mapKeys { it.key.toReference().getIdOrName() },
    references = getReferenceLinks().associateWithNotNull { getReferenceTargetRef(it)?.serialize() }
        .mapKeys { it.key.toReference().getIdOrName() },
    children = allChildren.map { it.asData() },
)

inline fun <K, V : Any> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    return associateWith { valueSelector(it) }.filterValues { it != null } as Map<K, V>
}
