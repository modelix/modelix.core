package org.modelix.metamodel.generator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.*
import java.io.File

@Serializable
data class ModelData(
    val id: String? = null,
    val root: NodeData,
) {
    fun toYaml(): String = Yaml.default.encodeToString(this)
    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    fun load(branch: IBranch) {
        branch.computeWriteT { t ->
            val createdNodes = HashMap<String, Long>()
            val pendingReferences = ArrayList<()->Unit>()
            val parentId = ITree.ROOT_ID
            for (nodeData in root.children) {
                loadNode(nodeData, t, parentId, createdNodes, pendingReferences)
            }
            pendingReferences.forEach { it() }
        }
    }

    private fun loadNode(
        nodeData: NodeData,
        t: IWriteTransaction,
        parentId: Long,
        createdNodes: HashMap<String, Long>,
        pendingReferences: ArrayList<() -> Unit>
    ) {
        val conceptRef = nodeData.concept?.let { ConceptReference(it) }
        val createdId = t.addNewChild(parentId, nodeData.role, -1, conceptRef)
        if (nodeData.id != null) {
            createdNodes[nodeData.id] = createdId
        }
        for (propertyData in nodeData.properties) {
            t.setProperty(createdId, propertyData.key, propertyData.value)
        }
        for (referenceData in nodeData.references) {
            pendingReferences += {
                val target = createdNodes[referenceData.value]?.let { LocalPNodeReference(it) }
                t.setReferenceTarget(createdId, referenceData.key, target)
            }
        }
        for (childData in nodeData.children) {
            loadNode(childData, t, createdId, createdNodes, pendingReferences)
        }
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true }
        fun fromFile(file: File): ModelData {
            return when (file.extension.lowercase()) {
                "yaml" -> fromYaml(file.readText())
                "json" -> fromJson(file.readText())
                else -> throw IllegalArgumentException("Unsupported file extension: $file")
            }
        }
        fun fromYaml(serialized: String): ModelData = Yaml.default.decodeFromString(serialized)
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
)

fun NodeData.uid(model: ModelData): String {
    return (model.id ?: throw IllegalArgumentException("Model has no ID")) +
            "/" +
            (id ?: throw IllegalArgumentException("Node has no ID"))
}
fun ModelData.nodeUID(node: NodeData): String = node.uid(this)