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

package org.modelix.mps.sync.mps.listeners

import com.jetbrains.rd.util.firstOrNull
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.SModelInternal
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.getNode
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.runIfAlone
import java.util.concurrent.atomic.AtomicReference

// TODO some methods need some testing
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleChangeListener(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val isSynchronizing: AtomicReference<Boolean>,
) : SModuleListener {

    override fun modelAdded(module: SModule, model: SModel) {
        isSynchronizing.runIfAlone {
            val moduleModelixId = nodeMap[module]!!
            val models = BuiltinLanguages.MPSRepositoryConcepts.Module.models

            branch.runWriteT {
                val cloudModule = branch.getNode(moduleModelixId)
                val cloudModel = cloudModule.addNewChild(models, -1, BuiltinLanguages.MPSRepositoryConcepts.Model)

                nodeMap.put(model, cloudModel.nodeIdAsLong())

                val nodeChangeListener = NodeChangeListener(branch, nodeMap, isSynchronizing)
                model.addChangeListener(nodeChangeListener)
                (model as? SModelInternal)?.addModelListener(
                    ModelChangeListener(
                        branch,
                        nodeMap,
                        nodeChangeListener,
                        isSynchronizing,
                    ),
                )

                // TODO trigger full model synchronization
            }
        }
    }

    override fun modelRemoved(module: SModule, reference: SModelReference) {
        isSynchronizing.runIfAlone {
            val moduleModelixId = nodeMap[module]!!
            val modelModelixId = nodeMap[reference.modelId]!!

            branch.runWriteT {
                val cloudModule = branch.getNode(moduleModelixId)
                val cloudModel = branch.getNode(modelModelixId)
                // cloudModule.removeChild(cloudModel)
            }
        }
    }

    override fun dependencyAdded(module: SModule, dependency: SDependency) {
        isSynchronizing.runIfAlone {
            // TODO #1 is it called at all by MPS?
            // TODO #2 might not work, we have to test it
            val moduleModelixId = nodeMap[module]!!
            val dependencies = BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies

            val moduleReference = dependency.targetModule
            val moduleId = moduleReference.moduleId

            val isExplicit = if (module is Solution) {
                module.moduleDescriptor.dependencies.any { it.moduleRef.moduleId == moduleId }
            } else {
                module.declaredDependencies.any { it.targetModule.moduleId == moduleId }
            }

            val version = (module as? Solution)?.let {
                it.moduleDescriptor.dependencyVersions.filter { dependencyVersion -> dependencyVersion.key == moduleReference }
                    .firstOrNull()?.value
            } ?: 0

            branch.runWriteT {
                val cloudModule = branch.getNode(moduleModelixId)
                val cloudDependency = cloudModule.addNewChild(
                    role = dependencies,
                    index = -1,
                    concept = BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency,
                )

                nodeMap.put(moduleReference, cloudDependency.nodeIdAsLong())

                // warning: might be fragile, because we synchronize the properties by hand
                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport,
                    dependency.isReexport.toString(),
                )

                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid,
                    moduleReference.moduleId.toString(),
                )

                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name,
                    moduleReference.moduleName,
                )

                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.explicit,
                    isExplicit.toString(),
                )

                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.version,
                    version.toString(),
                )

                cloudDependency.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.scope,
                    dependency.scope.toString(),
                )
            }
        }
    }

    override fun dependencyRemoved(module: SModule, dependency: SDependency) {
        isSynchronizing.runIfAlone {
            // TODO #1 is it called at all by MPS?
            // TODO #2 might not work, we have to test it
            val modelixId = nodeMap[module]!!

            val targetModule = dependency.targetModule
            val targetModuleId = nodeMap[targetModule]!!

            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                val dependencyChild = branch.getNode(targetModuleId)
                cloudNode.removeChild(dependencyChild)
            }
        }
    }

    override fun languageAdded(module: SModule, language: SLanguage) {
        isSynchronizing.runIfAlone {
            // TODO #1 is it called at all by MPS?
            // TODO #2 might not work, we have to test it
            // TODO #3 deduplicate, because it is handled in a very similar way in ModelChangeListener
            val modelixId = nodeMap[module]!!

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
                nodeMap.put(languageModuleReference, cloudLanguageDependency.nodeIdAsLong())

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
                    module.getUsedLanguageVersion(language).toString(),
                )
            }
        }
    }

    override fun languageRemoved(module: SModule, language: SLanguage) {
        isSynchronizing.runIfAlone {
            // TODO #1 is it called at all by MPS?
            // TODO #2 might not work, we have to test it
            // TODO #3 deduplicate, because it is handled in a very similar way in ModelChangeListener
            val modelixId = nodeMap[module]!!

            val languageModuleReference = language.sourceModuleReference
            val languageModuleReferenceModelixId = nodeMap[languageModuleReference]!!

            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                val cloudLanguageModuleReference = branch.getNode(languageModuleReferenceModelixId)
                cloudNode.removeChild(cloudLanguageModuleReference)
            }
        }
    }

    override fun beforeModelRemoved(module: SModule, model: SModel) {}
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}
    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {
        // duplicate of SModelListener.modelRenamed
    }

    override fun moduleChanged(module: SModule) {}
}
