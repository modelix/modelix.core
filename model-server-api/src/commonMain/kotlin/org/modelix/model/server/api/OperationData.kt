/*
 * Copyright (c) 2022.
 *
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

@Serializable
sealed class OperationData {
    abstract fun replaceIds(replacer: (String)->String?): OperationData
}

@Serializable
data class SetPropertyOpData(
    val node: NodeId,
    val role: String,
    val value: String?
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return SetPropertyOpData(replacer(node) ?: node, role, value)
    }
}

@Serializable
data class SetReferenceOpData(
    val node: NodeId,
    val role: String,
    val target: NodeId?
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return SetReferenceOpData(replacer(node) ?: node, role, target?.let(replacer) ?: target)
    }
}

@Serializable
data class AddNewChildNodeOpData(
    val parentNode: NodeId,
    val role: String?,
    val index: Int = -1,
    val concept: String?,
    val childId: NodeId
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return AddNewChildNodeOpData(replacer(parentNode) ?: parentNode, role, index, concept, replacer(childId) ?: childId)
    }
}

@Serializable
data class MoveNodeOpData(
    val newParentNode: NodeId,
    val newRole: String?,
    val newIndex: Int = -1,
    val childId: NodeId,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return MoveNodeOpData(replacer(newParentNode) ?: newParentNode, newRole, newIndex, replacer(childId) ?: childId)
    }
}

@Serializable
data class DeleteNodeOpData(
    val nodeId: NodeId,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return DeleteNodeOpData(replacer(nodeId) ?: nodeId)
    }
}

