/*
 * Copyright (c) 2022.
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class OperationData {
    abstract fun replaceIds(replacer: (String) -> String?): OperationData
}

@Serializable
@SerialName("setProperty")
data class SetPropertyOpData(
    val node: NodeId,
    val role: String,
    val value: String?,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return SetPropertyOpData(
            node = replacer(node) ?: node,
            role = role,
            value = value,
        )
    }
}

@Serializable
@SerialName("setReference")
data class SetReferenceOpData(
    val node: NodeId,
    val role: String,
    val target: NodeId?,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return SetReferenceOpData(
            node = replacer(node) ?: node,
            role = role,
            target = target?.let(replacer) ?: target,
        )
    }
}

@Serializable
@SerialName("addNew")
data class AddNewChildNodeOpData(
    val parentNode: NodeId,
    val role: String?,
    val index: Int = -1,
    val concept: String?,
    val childId: NodeId,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return AddNewChildNodeOpData(
            parentNode = replacer(parentNode) ?: parentNode,
            role = role,
            index = index,
            concept = concept,
            childId = replacer(childId) ?: childId,
        )
    }
}

@Serializable
@SerialName("move")
data class MoveNodeOpData(
    val newParentNode: NodeId,
    val newRole: String?,
    val newIndex: Int = -1,
    val childId: NodeId,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return MoveNodeOpData(
            newParentNode = replacer(newParentNode) ?: newParentNode,
            newRole = newRole,
            newIndex = newIndex,
            childId = replacer(childId) ?: childId,
        )
    }
}

@Serializable
@SerialName("delete")
data class DeleteNodeOpData(
    val nodeId: NodeId,
) : OperationData() {
    override fun replaceIds(replacer: (String) -> String?): OperationData {
        return DeleteNodeOpData(replacer(nodeId) ?: nodeId)
    }
}
