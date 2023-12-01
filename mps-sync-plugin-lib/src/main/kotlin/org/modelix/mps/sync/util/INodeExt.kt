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

import jetbrains.mps.smodel.SNodeId.Regular
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSNode

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.mappedMpsNodeID(): String? {
    return try {
        val nodeIdProperty = PropertyFromName(NodeData.ID_PROPERTY_KEY)
        this.getPropertyValue(nodeIdProperty)
    } catch (e: RuntimeException) {
        throw RuntimeException(
            "Failed to retrieve the ${NodeData.ID_PROPERTY_KEY} property in mappedMpsNodeID. The INode is $this , concept: ${this.concept}",
            e,
        )
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.nodeIdAsLong(): Long =
    when (this) {
        is PNodeAdapter -> this.nodeId
        is MPSNode -> {
            when (val nodeId = this.node.nodeId) {
                is Regular -> nodeId.id
                else -> throw IllegalStateException("Unsupported NodeId format: $nodeId")
            }
        }

        else -> throw IllegalStateException("Unsupported INode implementation")
    }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.isModule(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.isModel(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Model)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.isDevKitDependency(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.isSingleLanguageDependency(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.getModel(): INode? = findNode { it.isModel() }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun INode.getModule(): INode? = findNode { it.isModule() }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
private fun INode.findNode(criterion: (INode) -> Boolean): INode? {
    if (criterion(this)) {
        return this
    }

    var node = this.parent
    while (node != null) {
        if (criterion(node)) {
            return node
        }
        node = node.parent
    }

    return null
}
