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

import jetbrains.mps.lang.smodel.generator.smodelAdapter.SConceptOperations
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModel.Problem
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelListener
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeAccessListener
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.remove
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSModelReference
import org.modelix.model.mpsadapters.Model

data class NodeAsMPSModel(val node: INode, val sRepository: SRepository?) : SModel {
    companion object {
        fun wrap(modelNode: INode, repository: SRepository?): SModel = NodeAsMPSModel(modelNode, repository)
    }

    init {
        check(node.concept?.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Model) == true) { "Not a model: ${node.concept}" }
    }

    override fun addAccessListener(l: SNodeAccessListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addChangeListener(l: SNodeChangeListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addModelListener(l: SModelListener?) = throw UnsupportedOperationException("Not implemented")

    override fun addRootNode(node: SNode?) {
        if (node != null) {
            this.node.addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes, -1, MPSConcept(node.concept))
        }
    }

    override fun createNode(concept: SConcept): SNode? {
        return SConceptOperations.createNewNode(concept)
    }

    override fun createNode(concept: SConcept, nodeId: SNodeId?): SNode? {
        // TODO can we set the id somehow?
        return SConceptOperations.createNewNode(concept)
    }

    override fun getModelId(): SModelId {
        val serialized = checkNotNull(node.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)) {
            "No model id found"
        }
        return PersistenceFacade.getInstance().createModelId(serialized)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("getName()"))
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

    override fun getSource() = object : DataSource {
        override fun getLocation() = "modelix"
        override fun getTimestamp() = 0L
        override fun isReadOnly() = true
    }

    override fun isLoaded() = true

    override fun isReadOnly() = true

    override fun load() { /* no-op */ }

    override fun removeAccessListener(l: SNodeAccessListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeChangeListener(l: SNodeChangeListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeModelListener(l: SModelListener?) = throw UnsupportedOperationException("Not implemented")

    override fun removeRootNode(node: SNode?) {
        if (node == null) return

        val rootNodes = this.node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes)
        val toDelete = rootNodes.find { it.reference.serialize().endsWith(node.reference.toString()) }
        toDelete?.remove()
    }

    override fun unload() { /* no-op */ }

    override fun getReference(): SModelReference {
        val serialized = node.reference.serialize().substringAfter(MPSModelReference.PREFIX)
        return PersistenceFacade.getInstance().createModelReference(serialized)
    }

    override fun getNode(id: SNodeId?): SNode? {
        if (id == null) return null

        val nodeId = PersistenceFacade.getInstance().asString(id)
        val node = node.getDescendants(true).firstOrNull { it.reference.serialize().endsWith(nodeId) }
        return node?.let { NodeAsMPSNode(it, repository) }
    }

    override fun getProblems() = emptyList<Problem>()
}
