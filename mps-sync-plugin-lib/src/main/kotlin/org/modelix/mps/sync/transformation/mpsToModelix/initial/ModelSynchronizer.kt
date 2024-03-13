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

package org.modelix.mps.sync.transformation.mpsToModelix.initial

import jetbrains.mps.extapi.model.SModelBase
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelSynchronizer(private val branch: IBranch, postponeReferenceResolution: Boolean = false) {

    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val bindingsRegistry = BindingsRegistry

    private val nodeSynchronizer = if (postponeReferenceResolution) {
        NodeSynchronizer(branch, synchronizedLinkedHashSet())
    } else {
        NodeSynchronizer(branch)
    }

    private val resolvableModelImports = synchronizedLinkedHashSet<CloudResolvableModelImport>()

    fun addModelAndActivate(model: SModelBase) {
        addModel(model)
            .continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
                (it as IBinding).activate()
            }
    }

    fun addModel(model: SModelBase) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val moduleModelixId = nodeMap[model.module]!!
            val models = BuiltinLanguages.MPSRepositoryConcepts.Module.models

            val cloudModule = branch.getNode(moduleModelixId)
            val cloudModel = cloudModule.addNewChild(models, -1, BuiltinLanguages.MPSRepositoryConcepts.Model)

            nodeMap.put(model, cloudModel.nodeIdAsLong())

            synchronizeModelProperties(cloudModel, model)

            // synchronize root nodes
            model.rootNodes.waitForCompletionOfEachTask { nodeSynchronizer.addNode(it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // synchronize model imports
            model.modelImports.waitForCompletionOfEachTask { addModelImport(model, it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // synchronize language dependencies
            model.importedLanguageIds().waitForCompletionOfEachTask { addLanguageDependency(model, it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // synchronize devKits
            model.importedDevkits().waitForCompletionOfEachTask { addDevKitDependency(model, it) }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.MPS_TO_MODELIX) {
            val isDescriptorModel = model.name.value.endsWith("@descriptor")
            if (isDescriptorModel) {
                EmptyBinding()
            } else {
                // register binding
                val binding = ModelBinding(model, branch)
                bindingsRegistry.addModelBinding(binding)
                binding
            }
        }

    private fun synchronizeModelProperties(cloudModel: INode, model: SModel) {
        cloudModel.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id, model.modelId.toString())
        cloudModel.setPropertyValue(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
            model.name.value,
        )
        if (model.name.hasStereotype()) {
            cloudModel.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype,
                model.name.stereotype,
            )
        }
    }

    fun addModelImport(model: SModel, importedModelReference: SModelReference) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val targetModel = importedModelReference.resolve(model.repository)
            val isNotMapped = nodeMap[targetModel] == null

            if (isNotMapped) {
                resolvableModelImports.add(CloudResolvableModelImport(model, targetModel))
            } else {
                addModelImportToCloud(model, targetModel)
            }
        }

    private fun addModelImportToCloud(source: SModel, targetModel: SModel) {
        val modelixId = nodeMap[source]!!

        val modelImportsLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports
        val modelReferenceConcept = BuiltinLanguages.MPSRepositoryConcepts.ModelReference

        val cloudParentNode = branch.getNode(modelixId)
        val cloudModelReference = cloudParentNode.addNewChild(modelImportsLink, -1, modelReferenceConcept)

        nodeMap.put(source, targetModel.reference, cloudModelReference.nodeIdAsLong())

        // warning: might be fragile, because we synchronize the fields by hand
        val targetModelModelixId = nodeMap[targetModel]!!
        val cloudTargetModel = branch.getNode(targetModelModelixId)
        cloudModelReference.setReferenceTarget(
            BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model,
            cloudTargetModel,
        )
    }

    fun addLanguageDependency(model: SModel, language: SLanguage) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val modelixId = nodeMap[model]!!

            val languageModuleReference = language.sourceModuleReference
            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

            val cloudNode = branch.getNode(modelixId)
            val cloudLanguageDependency =
                cloudNode.addNewChild(
                    childLink,
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency,
                )

            nodeMap.put(model, languageModuleReference, cloudLanguageDependency.nodeIdAsLong())

            // warning: might be fragile, because we synchronize the properties by hand
            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                languageModuleReference?.moduleName,
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                languageModuleReference?.moduleId.toString(),
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version,
                model.module.getUsedLanguageVersion(language).toString(),
            )
        }

    fun addDevKitDependency(model: SModel, devKit: SModuleReference) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val modelixId = nodeMap[model]!!

            val repository = model.repository
            val devKitModuleId = devKit.moduleId
            val devKitModule = repository.getModule(devKitModuleId)

            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

            val cloudNode = branch.getNode(modelixId)
            val cloudDevKitDependency =
                cloudNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)
            nodeMap.put(model, devKit, cloudDevKitDependency.nodeIdAsLong())

            // warning: might be fragile, because we synchronize the properties by hand
            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                devKitModule?.moduleName,
            )

            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                devKitModule?.moduleId.toString(),
            )
        }

    fun resolveModelImportsInTask() =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            resolveModelImports()
        }

    fun resolveCrossModelReferences() {
        resolveModelImports()
        // resolve (cross-model) references
        nodeSynchronizer.resolveReferences()
    }

    private fun resolveModelImports() {
        resolvableModelImports.forEach { addModelImportToCloud(it.sourceModel, it.targetModel) }
        resolvableModelImports.clear()
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class CloudResolvableModelImport(
    val sourceModel: SModel,
    val targetModel: SModel,
)
