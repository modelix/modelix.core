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

package org.modelix.mps.sync.transformation.modelixToMps.transformers

import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.SModelId
import jetbrains.mps.smodel.SModelReference
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.createModel
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.mps.util.runWriteInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelTransformer(
    private val modelAccess: ModelAccess,
    private val nodeMap: MpsToModelixMap,
    private val repository: SRepository? = null,
) {

    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()
    fun transformToModel(
        iNode: INode,
        mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking,
    ) {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        if (name == "ISOExample@descriptor") {
            // TODO revert me: hardcoded workaround for demo!
            return
        }
        check(name != null) { "Model's ($iNode) name is null" }

        val moduleId = iNode.getModule()?.nodeIdAsLong()!!
        val module: SModule? = nodeMap.getModule(moduleId)
        check(module != null) { "Parent module with ID $moduleId is not found" }

        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
        check(serializedId.isNotEmpty()) { "Model's ($iNode) ID is empty" }
        val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

        lateinit var sModel: EditableSModel
        mpsWriteAction {
            sModel = module.createModel(name, modelId) as EditableSModel
            sModel.save()
        }
        nodeMap.put(sModel, iNode.nodeIdAsLong())

        // register model imports
        iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)
            .forEach { transformModelImport(it) }

        // TODO revert me: add MethodConfiguration and ThreatCatalog imports for demo
        addIsoCompositionModuleImport(sModel)
        importComMoraadDevkit(sModel)
        val methodConfiguration = repository?.getModel(SModelId.fromString("r:9e0bf89b-7c83-426e-8e13-cd21fab7b94a"))!!
        val catalog = repository.getModel(SModelId.fromString("r:a269539f-8e07-4b12-82b7-a8f38e6897c9"))!!
        importIsoComposition(methodConfiguration, catalog)
    }

    private fun addIsoCompositionModuleImport(model: SModel) {
        // TODO revert me: hardcoded method for demo!
        val reference = AtomicReference<SModule>()
        modelAccess.runReadBlocking {
            reference.set(model.repository?.getModule(ModuleId.regular(UUID.fromString("ea36efc9-242c-402d-9cd6-9b37c96aac34"))))
        }
        val isoComposition = reference.get()

        modelAccess.runWriteActionInEDTBlocking {
            (model.module as Solution).addDependency(isoComposition?.moduleReference!!, false)
        }
    }

    private fun importComMoraadDevkit(model: SModel) {
        // TODO revert me: hardcoded method for demo!
        val reference = AtomicReference<SModule>()
        modelAccess.runReadBlocking {
            reference.set(model.repository?.getModule(ModuleId.regular(UUID.fromString("9b903ecd-ba57-441e-8d7c-d3f1fbfcc047"))))
        }
        val dependentModule = reference.get()

        modelAccess.runWriteInEDTBlocking {
            val devKitModuleReference = (dependentModule as DevKit).moduleReference
            model.addDevKit(devKitModuleReference)
        }
    }

    private fun importIsoComposition(methodConfiguration: SModel, catalog: SModel) {
        // TODO revert me: hardcoded method for demo!
        val model = nodeMap.models.firstOrNull()!!
        modelAccess.runWriteInEDTBlocking {
            ModelImports(model).addModelImport(methodConfiguration.reference)
            ModelImports(model).addModelImport(catalog.reference)
        }
    }

    fun transformModelImport(iNode: INode) {
        val sourceModel = nodeMap.getModel(iNode.getModel()?.nodeIdAsLong())!!
        val targetModel = iNode.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
        val targetId = targetModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)!!
        resolvableModelImports.add(
            ResolvableModelImport(
                source = sourceModel,
                targetModelId = targetId,
                targetModelModelixId = targetModel.nodeIdAsLong(),
                modelReferenceNodeId = iNode.nodeIdAsLong(),
            ),
        )
    }

    fun resolveModelImports(
        repository: SRepository,
        mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking,
    ) {
        resolvableModelImports.forEach {
            val id = PersistenceFacade.getInstance().createModelId(it.targetModelId)
            val targetModel = (nodeMap.getModel(it.targetModelModelixId) ?: repository.getModel(id))!!
            nodeMap.put(targetModel, it.targetModelModelixId)

            val targetModule = targetModel.module
            val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
            val modelImport = SModelReference(moduleReference, id, targetModel.name)

            mpsWriteAction {
                ModelImports(it.source).addModelImport(modelImport)
            }
            nodeMap.put(modelImport, it.modelReferenceNodeId)
        }
    }

    fun clearResolvableModelImports() = resolvableModelImports.clear()
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ResolvableModelImport(
    val source: SModel,
    val targetModelId: String,
    val targetModelModelixId: Long,
    val modelReferenceNodeId: Long,
)
