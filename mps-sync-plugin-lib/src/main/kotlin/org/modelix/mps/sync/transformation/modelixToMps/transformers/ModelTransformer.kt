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
import org.modelix.model.api.INode
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
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelTransformer(private val nodeMap: MpsToModelixMap, private val syncQueue: SyncQueue) {

    private val logger = KotlinLogging.logger {}

    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()

    fun transformToModel(iNode: INode) {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Model's ($iNode) name is null" }

        val moduleId = iNode.getModule()?.nodeIdAsLong()!!
        val module: SModule? = nodeMap.getModule(moduleId)
        check(module != null) { "Parent module with ID $moduleId is not found" }

        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
        check(serializedId.isNotEmpty()) { "Model's ($iNode) ID is empty" }
        val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

        lateinit var sModel: EditableSModel
        syncQueue.enqueueBlocking(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
        ) {
            val modelDoesNotExist = module.getModel(modelId) == null
            if (modelDoesNotExist) {
                sModel = module.createModel(name, modelId) as EditableSModel
                sModel.save()
                nodeMap.put(sModel, iNode.nodeIdAsLong())
            }
        }

        // register model imports
        iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)
            .forEach { transformModelImport(it) }
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

    fun resolveModelImports(repository: SRepository) {
        resolvableModelImports.forEach {
            val id = PersistenceFacade.getInstance().createModelId(it.targetModelId)
            val targetModel = (nodeMap.getModel(it.targetModelModelixId) ?: repository.getModel(id))!!
            nodeMap.put(targetModel, it.targetModelModelixId)

            val targetModule = targetModel.module
            val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
            val modelImport = SModelReference(moduleReference, id, targetModel.name)

            val sourceModel = it.source
            syncQueue.enqueueBlocking(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                ModelImports(sourceModel).addModelImport(modelImport)
            }
            nodeMap.put(it.source, modelImport, it.modelReferenceNodeId)
        }
        resolvableModelImports.clear()
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
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ResolvableModelImport(
    val source: SModel,
    val targetModelId: String,
    val targetModelModelixId: Long,
    val modelReferenceNodeId: Long,
)
