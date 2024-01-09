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
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.SyncBarrier
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelSynchronizer(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val isSynchronizing: SyncBarrier,
    postponeReferenceResolution: Boolean = false,
) {

    private val nodeSynchronizer = if (postponeReferenceResolution) {
        NodeSynchronizer(branch, nodeMap, isSynchronizing, mutableListOf())
    } else {
        NodeSynchronizer(branch, nodeMap, isSynchronizing)
    }

    private val resolvableModelImports = if (postponeReferenceResolution) {
        mutableListOf<CloudResolvableModelImport>()
    } else {
        null
    }

    fun addModel(model: SModelBase) {
        isSynchronizing.runIfAlone {
            val binding = addModelUnprotected(model)
            binding.activate()
        }
    }

    fun addModelUnprotected(model: SModelBase): ModelBinding {
        val moduleModelixId = nodeMap[model.module]!!
        val models = BuiltinLanguages.MPSRepositoryConcepts.Module.models

        var binding: ModelBinding? = null
        branch.runWriteT {
            val cloudModule = branch.getNode(moduleModelixId)
            val cloudModel = cloudModule.addNewChild(models, -1, BuiltinLanguages.MPSRepositoryConcepts.Model)

            nodeMap.put(model, cloudModel.nodeIdAsLong())

            model.repository.modelAccess.runReadBlocking {
                synchronizeModelProperties(cloudModel, model)
                // synchronize root nodes
                model.rootNodes.forEach { nodeSynchronizer.addNodeUnprotected(it) }
                // synchronize model imports
                model.modelImports.forEach { addModelImportUnprotected(model, it) }
                // synchronize language dependencies
                model.importedLanguageIds().forEach { addLanguageDependencyUnprotected(model, it) }
                // synchronize devKits
                model.importedDevkits().forEach { addDevKitDependencyUnprotected(model, it) }
            }

            // register binding
            val mpsProject = ActiveMpsProjectInjector.activeProject!!
            val bindingsRegistry = BindingsRegistry.instance
            binding = ModelBinding(
                model,
                branch,
                nodeMap,
                isSynchronizing,
                mpsProject.modelAccess,
                BindingsRegistry.instance,
            )
            bindingsRegistry.addModelBinding(binding!!)
        }

        return binding!!
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

    fun addModelImport(model: SModel, importedModelReference: SModelReference) {
        isSynchronizing.runIfAlone {
            addModelImportUnprotected(model, importedModelReference)
        }
    }

    private fun addModelImportUnprotected(model: SModel, importedModelReference: SModelReference) {
        val targetModel = importedModelReference.resolve(model.repository)
        if (resolvableModelImports != null) {
            resolvableModelImports.add(CloudResolvableModelImport(model, targetModel))
        } else {
            addModelImportToCloud(model, targetModel)
        }
    }

    private fun addModelImportToCloud(source: SModel, targetModel: SModel) {
        val modelixId = nodeMap[source]!!

        val modelImportsLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports
        val modelReferenceConcept = BuiltinLanguages.MPSRepositoryConcepts.ModelReference

        branch.runWriteT {
            val cloudParentNode = branch.getNode(modelixId)
            val cloudModelReference = cloudParentNode.addNewChild(modelImportsLink, -1, modelReferenceConcept)

            // TODO fixme: might be fragile. What happens if we import the model in more than one places? Then we'll use the same targetModel.reference, but different modelixIds to it. Meaning, the newly created modelix ID will always override the older ones in the nodeMap.
            nodeMap.put(targetModel.reference, cloudModelReference.nodeIdAsLong(), source)

            // warning: might be fragile, because we synchronize the fields and properties by hand
            val targetModelModelixId = nodeMap[targetModel]!!
            val cloudTargetModel = branch.getNode(targetModelModelixId)
            cloudModelReference.setReferenceTarget(
                BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model,
                cloudTargetModel,
            )
        }
    }

    fun addLanguageDependency(model: SModel, language: SLanguage) {
        isSynchronizing.runIfAlone {
            addLanguageDependencyUnprotected(model, language)
        }
    }

    private fun addLanguageDependencyUnprotected(model: SModel, language: SLanguage) {
        val modelixId = nodeMap[model]!!

        val languageModuleReference = language.sourceModuleReference
        val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

        branch.runWriteT {
            val cloudNode = branch.getNode(modelixId)
            val cloudLanguageDependency =
                cloudNode.addNewChild(
                    childLink,
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency,
                )

            // TODO we might have to find a different traceability between the SingleLanguageDependency and the ModuleReference, so it works in the inverse direction too (in the ITreeToSTreeTransformer, when downloading Languages from the cloud)
            // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
            nodeMap.put(languageModuleReference, cloudLanguageDependency.nodeIdAsLong(), model)

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
    }

    fun addDevKitDependency(model: SModel, devKit: SModuleReference) {
        isSynchronizing.runIfAlone {
            addDevKitDependencyUnprotected(model, devKit)
        }
    }

    private fun addDevKitDependencyUnprotected(model: SModel, devKit: SModuleReference) {
        val modelixId = nodeMap[model]!!

        val repository = model.repository
        val devKitModuleId = devKit.moduleId
        val devKitModule = repository.getModule(devKitModuleId)

        val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

        branch.runWriteT {
            val cloudNode = branch.getNode(modelixId)
            val cloudDevKitDependency =
                cloudNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)

            // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ITreeToSTreeTransformer, when downloading DevKits from the cloud)
            // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
            nodeMap.put(devKit, cloudDevKitDependency.nodeIdAsLong(), model)

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
    }

    private fun resolveModelImportsUnprotected() {
        resolvableModelImports?.forEach { addModelImportToCloud(it.sourceModel, it.targetModel) }
        resolvableModelImports?.clear()
    }

    fun resolveCrossModelReferences() {
        resolveModelImportsUnprotected()

        // resolve (cross-model) references
        nodeSynchronizer.resolveReferencesUnprotected()
        nodeSynchronizer.clearResolvableReferences()
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class CloudResolvableModelImport(
    val sourceModel: SModel,
    val targetModel: SModel,
)
