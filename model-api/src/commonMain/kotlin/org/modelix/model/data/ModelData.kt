/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference

@Serializable
data class ModelData(
    val id: String? = null,
    val root: NodeData,
) {
    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    fun load(branch: IBranch) {
        branch.computeWriteT { t ->
            val createdNodes = HashMap<String, Long>()
            val pendingReferences = ArrayList<() -> Unit>()
            val parentId = ITree.ROOT_ID
            if (root.id != null) {
                createdNodes[root.id] = parentId
            }
            setOriginalId(root, t, parentId)
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
        pendingReferences: ArrayList<() -> Unit>,
    ) {
        val conceptRef = nodeData.concept?.let { ConceptReference(it) }
        val createdId = t.addNewChild(parentId, nodeData.role, -1, conceptRef)
        if (nodeData.id != null) {
            createdNodes[nodeData.id] = createdId
            setOriginalId(nodeData, t, createdId)
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
    companion object {
        const val idPropertyKey = "#mpsNodeId#"
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
    concept = concept?.getUID(),
    role = roleInParent,
    properties = getPropertyRoles().associateWithNotNull { getPropertyValue(it) },
    references = getReferenceRoles().associateWithNotNull { getReferenceTargetRef(it)?.serialize() },
    children = allChildren.map { it.asData() },
)

inline fun <K, V : Any> Iterable<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    return associateWith { valueSelector(it) }.filterValues { it != null } as Map<K, V>
}
