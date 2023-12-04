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

import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.ModelImports
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
import org.modelix.mps.sync.mps.util.createModel
import org.modelix.mps.sync.mps.util.runWriteActionCommandBlocking
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.mps.util.runWriteInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelTransformer(private val modelAccess: ModelAccess, private val nodeMap: MpsToModelixMap) {

    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()
    fun transformToModel(iNode: INode, isInEDT: Boolean = false) {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Model's ($iNode) name is null" }

        val moduleId = iNode.getModule()?.nodeIdAsLong()!!
        val module: SModule? = nodeMap.getModule(moduleId)
        check(module != null) { "Parent module with ID $moduleId is not found" }

        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
        check(serializedId.isNotEmpty()) { "Model's ($iNode) ID is empty" }
        val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

        lateinit var sModel: EditableSModel
        val createModelAction = {
            sModel = module.createModel(name, modelId) as EditableSModel
            sModel.save()
        }
        if (isInEDT) {
            modelAccess.runWriteActionCommandBlocking(createModelAction)
        } else {
            modelAccess.runWriteActionInEDTBlocking(createModelAction)
        }

        nodeMap.put(sModel, iNode.nodeIdAsLong())

        // register model imports
        iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).forEach {
            val targetModel = it.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
            val targetId = targetModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)!!
            resolvableModelImports.add(
                ResolvableModelImport(
                    source = sModel,
                    targetModelId = targetId,
                    targetModelModelixId = targetModel.nodeIdAsLong(),
                    modelReferenceNodeId = it.nodeIdAsLong(),
                ),
            )
        }
    }

    fun resolveModelImports(repository: SRepository) {
        resolvableModelImports.forEach {
            val id = PersistenceFacade.getInstance().createModelId(it.targetModelId)
            val targetModel = (nodeMap.getModel(it.targetModelModelixId) ?: repository.getModel(id))!!
            nodeMap.put(targetModel, it.targetModelModelixId)

            val targetModule = targetModel.module
            val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
            val modelImport = SModelReference(moduleReference, id, targetModel.name)

            nodeMap.put(modelImport, it.modelReferenceNodeId)
            modelAccess.runWriteInEDTBlocking {
                ModelImports(it.source).addModelImport(modelImport)
            }
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
