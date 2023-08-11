/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server.api

import kotlinx.serialization.Serializable

typealias NodeId = String

@Serializable
data class NodeData(
    val nodeId: NodeId,
    val concept: String? = null,
    val parent: NodeId? = null,
    val role: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
    val children: Map<String?, List<NodeId>> = emptyMap(),
)

fun NodeData.replaceReferences(f: (Map<String, String>) -> Map<String, String>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = f(references),
        children = children,
    )
}

fun NodeData.replaceChildren(f: (Map<String?, List<String>>) -> Map<String?, List<String>>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = f(children),
    )
}

fun NodeData.replaceChildren(role: String?, f: (List<String>) -> List<String>): NodeData {
    return replaceChildren { allOldChildren ->
        val oldChildren = (allOldChildren[role] ?: emptyList()).toMutableList()
        val newChildren = f(oldChildren)
        allOldChildren + (role to newChildren)
    }
}

fun NodeData.replaceContainment(newParent: NodeId?, newRole: String?): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = newParent,
        role = newRole,
        properties = properties,
        references = references,
        children = children,
    )
}

fun NodeData.replaceId(newId: String): NodeData {
    return NodeData(
        nodeId = newId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = children,
    )
}

fun NodeData.allReferencedIds(): List<NodeId> {
    return children.values.flatten() + references.values + listOfNotNull(parent)
}
