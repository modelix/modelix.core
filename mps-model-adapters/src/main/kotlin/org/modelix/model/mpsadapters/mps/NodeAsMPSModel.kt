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

package org.modelix.model.mpsadapters.mps

import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelListener
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessListener
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.Model

class NodeAsMPSModel private constructor(private val node: INode, private val sRepository: SRepository?) : SModel {
    companion object {
        fun wrap(modelNode: INode, repository: SRepository?): SModel = NodeAsMPSModel(modelNode, repository)
    }

    init {
        check(node.concept?.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Model) == true) { "Not a model: ${node.concept}" }
    }

    override fun addAccessListener(l: SNodeAccessListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addChangeListener(l: SNodeChangeListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addModelListener(l: SModelListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addRootNode(node: SNode?) = throw UnsupportedOperationException("Not implemented")

    override fun createNode(concept: SConcept) = throw UnsupportedOperationException("Not implemented")

    override fun createNode(concept: SConcept, nodeId: SNodeId?) =
        throw UnsupportedOperationException("Not implemented")

    override fun getModelId() = throw UnsupportedOperationException("Not implemented")

    @Deprecated("Deprecated in Java")
    override fun getModelName() = name.value

    override fun getModelRoot() = throw UnsupportedOperationException("Not implemented")

    override fun getModule() = node.parent?.let { NodeAsMPSModule.wrap(it, sRepository) }

    override fun getName() =
        SModelName(node.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)!!)

    override fun getRepository(): SRepository = sRepository ?: MPSModuleRepository.getInstance()

    override fun getRootNodes() = node.getChildren(Model.rootNodes).map {
        val adapter = NodeAsMPSNode.wrap(it) as NodeAsMPSNode
        adapter.modelMode = EModelMode.ADAPTER
        adapter
    }

    override fun getSource() = throw UnsupportedOperationException("Not implemented")

    override fun isLoaded() = true

    override fun isReadOnly() = true

    override fun load() = throw UnsupportedOperationException("Not implemented")

    override fun removeAccessListener(l: SNodeAccessListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeChangeListener(l: SNodeChangeListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeModelListener(l: SModelListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeRootNode(node: SNode?) = throw UnsupportedOperationException("Not implemented")

    override fun unload() = throw UnsupportedOperationException("Not implemented")

    override fun getReference() = throw UnsupportedOperationException("Not implemented")

    override fun getNode(id: SNodeId?) = throw UnsupportedOperationException("Not implemented")

    override fun getProblems() = throw UnsupportedOperationException("Not implemented")

    override fun equals(other: Any?) =
        if (this === other) {
            true
        } else if (other == null || other !is NodeAsMPSModel) {
            false
        } else {
            node != other.node
        }

    override fun hashCode() = 31 + node.hashCode()
}
