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

import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.SModelReference
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.mps.util.ModelRenameHelper
import org.modelix.mps.sync.mps.util.createModel
import org.modelix.mps.sync.mps.util.deleteDevKit
import org.modelix.mps.sync.mps.util.deleteLanguage
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.ModelWithModelReference
import org.modelix.mps.sync.transformation.cache.ModelWithModuleReference
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelTransformer(private val branch: IBranch, mpsLanguageRepository: MPSLanguageRepository) {

    private val logger = KotlinLogging.logger {}
    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue

    private val nodeTransformer = NodeTransformer(branch, mpsLanguageRepository)
    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()

    fun transformToModelCompletely(nodeId: Long, branch: IBranch, bindingsRegistry: BindingsRegistry) =
        transformToModel(nodeId)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val model = branch.getNode(nodeId)
                // transform nodes
                model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).waitForCompletionOfEachTask {
                    nodeTransformer.transformToNode(it)
                }
            }.continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val model = branch.getNode(nodeId)
                // transform language or DevKit dependencies
                model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)
                    .waitForCompletionOfEachTask {
                        nodeTransformer.transformLanguageOrDevKitDependency(it)
                    }
            }.continueWith(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
                val iNode = branch.getNode(nodeId)
                if (isDescriptorModel(iNode)) {
                    EmptyBinding()
                } else {
                    // register binding
                    val model = nodeMap.getModel(iNode.nodeIdAsLong()) as SModelBase
                    val binding = ModelBinding(model, branch)
                    bindingsRegistry.addModelBinding(binding)
                    binding
                }
            }

    fun transformToModel(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            check(name != null) { "Model's ($iNode) name is null" }

            val moduleId = iNode.getModule()?.nodeIdAsLong()!!
            val module: SModule? = nodeMap.getModule(moduleId)
            check(module != null) { "Parent module with ID $moduleId is not found" }

            val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
            check(serializedId.isNotEmpty()) { "Model's ($iNode) ID is empty" }
            val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

            if (!isDescriptorModel(iNode)) {
                val sModel = module.createModel(name, modelId) as EditableSModel
                sModel.save()
                nodeMap.put(sModel, iNode.nodeIdAsLong())
            }

            // register model imports
            iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).waitForCompletionOfEachTask {
                transformModelImport(it.nodeIdAsLong())
            }
        }

    fun transformModelImport(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
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

    fun resolveModelImports(repository: SRepository) {
        resolvableModelImports.forEach {
            val id = PersistenceFacade.getInstance().createModelId(it.targetModelId)
            val targetModel = (nodeMap.getModel(it.targetModelModelixId) ?: repository.getModel(id))
            if (targetModel == null) {
                /*
                 * Issue MODELIX-819:
                 * A manual quick-fix would be if the user downloads the target model (+ its container module) into
                 * their MPS project, then create a module dependency between the source module and the target model's
                 * module, because that is the most probable cause of the problem.
                 * TODO (1) Show it as a suggestion to the user?
                 * TODO (2) If the model is not on the server then there is no 100% sure way to fix the issue, unless
                 * the model has the same ModelId as what is in the ModelImport and that model can be uploaded to the
                 * server. After that see suggestion above TODO (1) to fix the issue.
                 * TODO (3) As a final fallback, the user could remove the model import. In this case, we have to
                 * implement the corresponding feature (e.g., as an action by clicking on a button). Maybe this
                 * direction would be easier for the user and for us too.
                 */
                throw NoSuchElementException("ModelImport from Model ${it.source.modelId}(${it.source.name}) to Model $id cannot be resolved, because target model is not found.")
            }
            nodeMap.put(targetModel, it.targetModelModelixId)

            val targetModule = targetModel.module
            val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
            val modelImport = SModelReference(moduleReference, id, targetModel.name)

            val sourceModel = it.source
            ModelImports(sourceModel).addModelImport(modelImport)
            nodeMap.put(it.source, modelImport, it.modelReferenceNodeId)
        }
        resolvableModelImports.clear()
    }

    fun resolveCrossModelReferences(repository: SRepository) {
        resolveModelImports(repository)
        nodeTransformer.resolveReferences()
    }

    fun modelPropertyChanged(sModel: SModel, role: String, newValue: String?, nodeId: Long) {
        val modelId = sModel.modelId

        if (role == BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName()) {
            val oldValue = sModel.name.value
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    logger.error { "Name cannot be null or empty for Model $modelId. Corresponding Modelix Node ID is $nodeId." }
                    return
                } else if (sModel !is EditableSModelBase) {
                    logger.error { "SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Modelix Node ID is $nodeId." }
                    return
                }

                ModelRenameHelper(sModel).renameModel(newValue)
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype.getSimpleName()) {
            val oldValue = sModel.name.stereotype
            if (oldValue != newValue) {
                if (sModel !is EditableSModelBase) {
                    logger.error { "SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Modelix Node ID is $nodeId." }
                    return
                }

                ModelRenameHelper(sModel).changeStereotype(newValue)
            }
        } else {
            logger.error { "Role $role is unknown for concept Model. Therefore the property is not set in MPS from Modelix Node $nodeId" }
        }
    }

    fun modelMovedToNewParent(newParentId: Long, nodeId: Long, sModel: SModel) {
        val newParentModule = nodeMap.getModule(newParentId)
        if (newParentModule == null) {
            logger.error { "Modelix Node ($nodeId) that is a Model, was not moved to a new parent module, because new parent Module (Modelix Node $newParentId) was not mapped to MPS yet." }
            return
        }

        val oldParentModule = sModel.module
        if (oldParentModule == newParentModule) {
            return
        }

        // remove from old parent
        if (oldParentModule !is SModuleBase) {
            logger.error { "Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase. Therefore parent of Modelix Node $nodeId was not changed in MPS." }
            return
        } else if (sModel !is SModelBase) {
            logger.error { "Model ${sModel.modelId} is not an SModelBase" }
            return
        }
        oldParentModule.unregisterModel(sModel)
        sModel.module = null

        // add to new parent
        if (newParentModule !is SModuleBase) {
            logger.error { "New parent Module ${newParentModule.moduleId} is not an SModuleBase. Therefore parent of Modelix Node $nodeId was not changed in MPS." }
            return
        }
        newParentModule.registerModel(sModel)
    }

    fun modelDeleted(sModel: SModel, nodeId: Long) {
        ModelDeleteHelper(sModel).delete()
        nodeMap.remove(nodeId)
    }

    fun modeImportDeleted(outgoingModelReference: ModelWithModelReference) {
        ModelImports(outgoingModelReference.source).removeModelImport(outgoingModelReference.modelReference)
    }

    fun moduleDependencyOfModelDeleted(modelWithModuleReference: ModelWithModuleReference, nodeId: Long) {
        val sourceModel = modelWithModuleReference.source
        val targetModuleReference = modelWithModuleReference.moduleReference
        when (val targetModule = targetModuleReference.resolve(sourceModel.repository)) {
            is Language -> {
                try {
                    val sLanguage = MetaAdapterFactory.getLanguage(targetModuleReference)
                    sourceModel.deleteLanguage(sLanguage)
                } catch (ex: Exception) {
                    val message =
                        "Language import ($targetModule) cannot be deleted, because ${ex.message} Corresponding Modelix Node ID is $nodeId."
                    logger.error(ex) { message }
                }
            }

            is DevKit -> {
                try {
                    sourceModel.deleteDevKit(targetModuleReference)
                } catch (ex: Exception) {
                    val message =
                        "DevKit dependency ($targetModule) cannot be deleted, because ${ex.message} Corresponding Modelix Node ID is $nodeId."
                    logger.error(ex) { message }
                }
            }

            else -> {
                logger.error { "Target module referred by $targetModuleReference is neither a Language nor DevKit. Therefore the dependency for it cannot be deleted. Corresponding Modelix Node ID is $nodeId." }
            }
        }
    }

    private fun isDescriptorModel(iNode: INode): Boolean {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        return iNode.isModel() && name?.endsWith("@descriptor") == true
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ResolvableModelImport(
    val source: SModel,
    val targetModelId: String,
    val targetModelModelixId: Long,
    val modelReferenceNodeId: Long,
)
