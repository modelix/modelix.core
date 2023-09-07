/*
 * Copyright (c) 2023.
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

package org.modelix.mps.sync.util

import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.mps.sync.binding.ModelSynchronizer

val MPS_NODE_ID_PROPERTY_NAME: String = ModelSynchronizer.MPS_NODE_ID_PROPERTY_NAME

fun INode.mapToMpsNode(mpsNode: SNode) {
    val property = PropertyFromName(MPS_NODE_ID_PROPERTY_NAME)
    this.setPropertyValue(property, mpsNode.nodeId.toString())
}

fun INode.mappedMpsNodeID(): String? {
    return try {
        val property = PropertyFromName(MPS_NODE_ID_PROPERTY_NAME)
        this.getPropertyValue(property)
    } catch (e: RuntimeException) {
        throw RuntimeException(
            "Failed to retrieve the $MPS_NODE_ID_PROPERTY_NAME property in mappedMpsNodeID. The INode is $this , concept: ${this.concept}",
            e,
        )
    }
}

fun INode.isMappedToMpsNode() = this.mappedMpsNodeID() != null
