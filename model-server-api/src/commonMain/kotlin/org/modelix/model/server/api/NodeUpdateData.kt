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
package org.modelix.model.server.api

import kotlinx.serialization.Serializable

@Serializable
data class NodeUpdateData(
    val nodeId: NodeId? = null,
    val temporaryNodeId: NodeId? = null,
    // val parent: NodeId? = null,
    // val role: String? = null,
    // val index: Int? = null, // when a new node is created, this is the position in the parent
    val concept: String? = null,
    val references: Map<String, String?>? = null,
    val properties: Map<String, String?>? = null,
    val children: Map<String?, List<NodeId>>? = null,
) {
    fun replaceIds(replacer: (String) -> String?): NodeUpdateData {
        val replaceOrKeep: (String) -> String = { replacer(it) ?: it }

        val newNodeId = nodeId ?: temporaryNodeId?.let(replacer)
        return NodeUpdateData(
            nodeId = newNodeId,
            temporaryNodeId = temporaryNodeId,
//            parent = parent?.let(replaceOrKeep),
//            role = role,
//            index = index,
            concept = concept,
            references = references?.mapValues { it.value?.let(replaceOrKeep) },
            properties = properties,
            children = children?.mapValues { it.value.map(replaceOrKeep) },
        )
    }

    fun withReference(referenceRole: String, target: NodeId?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = (references ?: emptyMap()) + (referenceRole to target),
        properties = properties,
        children = children,
    )
    fun withProperty(propertyRole: String, value: String?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = references,
        properties = (properties ?: emptyMap()) + (propertyRole to value),
        children = children,
    )
    fun withChildren(childrenRole: String?, newChildren: List<NodeId>?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = references,
        properties = properties,
        children = (children ?: emptyMap()) + (childrenRole to (newChildren ?: emptyList())),
    )
    fun withConcept(newConceptUID: String?): NodeUpdateData {
        return NodeUpdateData(
            nodeId = nodeId,
            temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
            concept = newConceptUID,
            references = references,
            properties = properties,
            children = children,
        )
    }

//    fun withContainment(newParent: NodeId, newRole: String?, newIndex: Int) = NodeUpdateData(
//        nodeId = nodeId,
//        temporaryNodeId = temporaryNodeId,
// //        parent = newParent,
// //        role = newRole,
// //        index = newIndex,
//        concept = concept,
//        references = references,
//        properties = properties,
//        children = children
//    )

    companion object {
        fun newNode(tempId: NodeId, concept: String?) = NodeUpdateData(
            nodeId = null,
            temporaryNodeId = tempId,
            concept = concept,
            references = null,
            properties = null,
            children = null,
        )

        fun nothing(nodeId: NodeId) = NodeUpdateData(nodeId = nodeId)
    }
}
