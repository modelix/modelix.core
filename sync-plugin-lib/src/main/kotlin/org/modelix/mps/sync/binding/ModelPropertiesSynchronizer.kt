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

package org.modelix.mps.sync.binding

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.lang.migration.runtime.base.VersionFixer
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.PArea
import org.modelix.model.mpsadapters.DevKitDependencyAsNode
import org.modelix.model.mpsadapters.MPSModelAsNode
import org.modelix.model.mpsadapters.NodeAsMPSNode
import org.modelix.model.mpsadapters.SingleLanguageDependencyAsNode
import org.modelix.mps.sync.replication.ICloudRepository
import org.modelix.mps.sync.util.addDevKit
import org.modelix.mps.sync.util.addLanguageImport
import org.modelix.mps.sync.util.copyProperty
import org.modelix.mps.sync.util.copyPropertyIfNecessary
import org.modelix.mps.sync.util.replicateChild
import org.modelix.mps.sync.util.runInWriteActionIfNeeded
import java.util.UUID

// status: ready to test
class ModelPropertiesSynchronizer(
    private val modelNodeId: Long,
    private val model: SModel,
    private val cloudRepository: ICloudRepository,
) {
    companion object {
        private val logger = logger<ModelPropertiesSynchronizer>()

        fun syncModelPropertiesToMPS(tree: ITree, model: SModel, modelNodeId: Long, cloudRepository: ICloudRepository) {
            syncUsedLanguagesAndDevKitsToMPS(tree, model, modelNodeId, cloudRepository)
            syncModelImportsToMPS(tree, model, modelNodeId, cloudRepository)

            try {
                val projects = ProjectManager.getInstance().openedProjects
                if (projects.isNotEmpty()) {
                    VersionFixer(projects.first(), model.module, true).updateImportVersions()

                    // TODO use ModuleDependencyVersions when switching for MPS 2021.3
                    /*val repository = model.module.repository!!
                    val updater = ModuleDependencyVersions(LanguageRegistry.getInstance(repository), repository)
                    updater.resetVersions()
                    updater.update(model.module)*/
                }
            } catch (ex: Exception) {
                logger.error("Failed to update language version after change in model ${model.name.value}", ex)
            }
        }

        private fun syncUsedLanguagesAndDevKitsToMPS(
            tree: ITree,
            model: SModel,
            modelNodeId: Long,
            cloudRepository: ICloudRepository,
        ) {
            PArea(cloudRepository.getBranch()).executeRead {
                model.runInWriteActionIfNeeded {
                    // First get the dependencies in MPS
                    val mpsModelNode = MPSModelAsNode.wrap(model)!!
                    val dependenciesInMPS: List<INode> =
                        mpsModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).toList()

                    // Then get the dependencies in the cloud
                    val branch = cloudRepository.getBranch()
                    val cloudModelNode = PNodeAdapter(modelNodeId, branch)
                    val dependenciesInCloud =
                        cloudModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)

                    // For each import in the cloud add it if not present in MPS or otherwise ensure all properties are the same
                    dependenciesInCloud.forEach { dependencyInCloud ->
                        val matchingDependencyInMPS = dependenciesInMPS.firstOrNull { dependencyInMPS ->
                            val uuidProperty = BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid
                            dependencyInCloud.getPropertyValue(uuidProperty) == dependencyInMPS.getPropertyValue(
                                uuidProperty,
                            )
                        }
                        if (matchingDependencyInMPS == null) {
                            if (dependencyInCloud.concept?.getLongName() == BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency.getLongName()) {
                                val repo = model.repository
                                val devKitUUID =
                                    dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
                                val devKit = repo.getModule(ModuleId.regular(UUID.fromString(devKitUUID))) as DevKit
                                val devKitModuleReference = devKit.moduleReference
                                mpsModelNode.model.addDevKit(devKitModuleReference)
                            } else if (dependencyInCloud.concept?.getLongName() == BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.getLongName()) {
                                val repo = model.repository
                                val languageUUID =
                                    dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
                                val language =
                                    repo.getModule(ModuleId.regular(UUID.fromString(languageUUID))) as Language
                                val sLanguage = MetaAdapterFactory.getLanguage(language.moduleReference)
                                mpsModelNode.model.addLanguageImport(
                                    sLanguage,
                                    Integer.parseInt(dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)),
                                )
                            } else {
                                throw UnsupportedOperationException("Unknown dependency with concept ${dependencyInCloud.concept?.getLongName()}")
                            }
                        } else {
                            // We use this method to avoid using set, if it is not strictly necessary, which may be not supported
                            matchingDependencyInMPS.copyPropertyIfNecessary(
                                dependencyInCloud,
                                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                            )
                            matchingDependencyInMPS.copyPropertyIfNecessary(
                                dependencyInCloud,
                                BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version,
                            )
                        }
                    }

                    // For each import not in Cloud remove it
                    dependenciesInMPS.forEach { dependencyInMPS ->
                        if (dependencyInMPS is DevKitDependencyAsNode) {
                            var matchingDependencyInCloud: INode? = null
                            dependenciesInCloud.forEach { dependencyInCloud ->
                                if (dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(
                                        BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                                    )
                                ) {
                                    matchingDependencyInCloud = dependencyInCloud
                                }
                            }
                            if (matchingDependencyInCloud == null) {
                                val moduleReference = dependencyInMPS.moduleReference!!
                                val languageToRemove = MetaAdapterFactory.getLanguage(moduleReference)
                                if (mpsModelNode.model is SModelDescriptorStub) {
                                    (mpsModelNode.model as SModelDescriptorStub).deleteLanguageId(languageToRemove)
                                }
                            }
                        } else if (dependencyInMPS is SingleLanguageDependencyAsNode) {
                            var matchingDependencyInCloud: INode? = null
                            dependenciesInCloud.forEach { dependencyInCloud ->
                                if (dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(
                                        BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                                    )
                                ) {
                                    matchingDependencyInCloud = dependencyInCloud
                                }
                            }
                            if (matchingDependencyInCloud == null) {
                                val moduleReference = dependencyInMPS.moduleReference!!
                                val languageToRemove = MetaAdapterFactory.getLanguage(moduleReference)
                                if (mpsModelNode.model is SModelDescriptorStub) {
                                    (mpsModelNode.model as SModelDescriptorStub).deleteLanguageId(languageToRemove)
                                }
                            }
                        } else {
                            throw RuntimeException("Unknown dependency type: ${dependencyInMPS.javaClass.name}")
                        }
                    }
                }
            }
        }

        private fun syncModelImportsToMPS(
            tree: ITree,
            model: SModel,
            modelNodeId: Long,
            cloudRepository: ICloudRepository,
        ) {
            PArea(cloudRepository.getBranch()).executeRead {
                model.runInWriteActionIfNeeded {
                    // First get the dependencies in MPS
                    val mpsModelNode = MPSModelAsNode.wrap(model)!!
                    val dependenciesInMPS =
                        mpsModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)

                    // Then get the dependencies in the cloud
                    val branch = cloudRepository.getBranch()
                    val cloudModelNode = PNodeAdapter(modelNodeId, branch)
                    val dependenciesInCloud =
                        cloudModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)

                    // For each import in Cloud add it if not present in MPS or otherwise ensure all properties are the same
                    dependenciesInCloud.forEach { dependencyInCloud ->
                        val modelImportedInCloud =
                            dependencyInCloud.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                        if (modelImportedInCloud != null) {
                            val idProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.id
                            val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                            val matchingDependencyInMps = dependenciesInMPS.firstOrNull { dependencyInMPS ->
                                val modelImportedInMPS =
                                    dependencyInMPS.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                                if (modelImportedInMPS == null) {
                                    false
                                } else {
                                    val modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(idProperty)
                                    modelIDimportedInCloud == modelIDimportedInMPS
                                }
                            }
                            if (matchingDependencyInMps == null) {
                                mpsModelNode.replicateChild(
                                    BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports,
                                    dependencyInCloud,
                                )
                            } else {
                                // no properties to set here
                            }
                        }
                    }

                    // For each import not in Cloud remove it
                    dependenciesInMPS.forEach { dependencyInMPS ->
                        val modelImportedInMPS =
                            dependencyInMPS.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                        if (modelImportedInMPS != null) {
                            val idProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.id
                            val modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(idProperty)
                            val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                                val modelImportedInCloud =
                                    dependencyInCloud.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                                if (modelImportedInCloud == null) {
                                    false
                                } else {
                                    val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                                    modelIDimportedInMPS == modelIDimportedInCloud
                                }
                            }
                            if (matchingDependencyInCloud == null) {
                                val modelReferenceToRemove: SModelReference =
                                    NodeAsMPSNode.wrap(dependencyInMPS, model.repository)?.model?.reference!!
                                if (model is SModelDescriptorStub) {
                                    model.deleteModelImport(modelReferenceToRemove)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val branch: IBranch
        get() = cloudRepository.getBranch()

    fun syncModelPropertiesFromMPS() {
        PArea(branch).executeWrite {
            syncUsedLanguagesAndDevKitsFromMPS()
            syncModelImportsFromMPS()
        }
    }

    fun syncModelPropertiesToMPS(tree: ITree, cloudRepository: ICloudRepository) =
        syncModelPropertiesToMPS(tree, model, modelNodeId, cloudRepository)

    fun syncUsedLanguagesAndDevKitsFromMPS() {
        PArea(branch).executeWrite {
            // First get the dependencies in MPS
            val mpsModelNode = MPSModelAsNode.wrap(model)!!
            val dependenciesInMPS: List<INode> =
                mpsModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).toList()

            // Then get the dependencies in the cloud
            val cloudModelNode = PNodeAdapter(modelNodeId, branch)
            val dependenciesInCloud =
                cloudModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)

            // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
            dependenciesInMPS.forEach { dependencyInMPS ->
                if (dependencyInMPS is DevKitDependencyAsNode) {
                    val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                        dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(
                            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                        )
                    }
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChild(
                            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages,
                            dependencyInMPS,
                        )
                    } else {
                        cloudModelNode.copyProperty(
                            dependencyInMPS,
                            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                        )
                    }
                } else if (dependencyInMPS is SingleLanguageDependencyAsNode) {
                    val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                        dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(
                            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                        )
                    }
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChild(
                            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages,
                            dependencyInMPS,
                        )
                    } else {
                        cloudModelNode.copyProperty(
                            dependencyInMPS,
                            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                        )
                        cloudModelNode.copyProperty(
                            dependencyInMPS,
                            BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version,
                        )
                    }
                } else {
                    throw RuntimeException("Unknown dependency type: ${dependencyInMPS.javaClass.name}")
                }
            }

            // For each import not in MPS, remove it
            dependenciesInCloud.forEach { dependencyInCloud ->
                val matchingDependencyInMPS = dependenciesInCloud.firstOrNull { dependencyInMPS ->
                    val uuidProperty = BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid
                    dependencyInCloud.getPropertyValue(uuidProperty) == dependencyInMPS.getPropertyValue(uuidProperty)
                }
                if (matchingDependencyInMPS == null) {
                    cloudModelNode.removeChild(dependencyInCloud)
                }
            }
        }
    }

    fun syncModelImportsFromMPS() {
        PArea(branch).executeWrite {
            // First get the dependencies in MPS. Model imports do not include implicit ones e
            val mpsModelNode = MPSModelAsNode.wrap(model)!!
            val dependenciesInMPS = mpsModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)

            // Then get the dependencies in the cloud
            val cloudModelNode = PNodeAdapter(modelNodeId, branch)
            val dependenciesInCloud =
                cloudModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)

            // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
            dependenciesInMPS.forEach { dependencyInMPS ->
                val modelImportedInMps =
                    dependencyInMPS.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                if (modelImportedInMps != null) {
                    val idProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.id
                    val modelIDimportedInMPS = modelImportedInMps.getPropertyValue(idProperty)
                    val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                        val modelImportedInCloud =
                            dependencyInCloud.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                        if (modelImportedInCloud == null) {
                            false
                        } else {
                            val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                            modelIDimportedInMPS == modelIDimportedInCloud
                        }
                    }
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChild(
                            BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports,
                            dependencyInMPS,
                        )
                    } else {
                        // no properties to set here
                    }
                }
            }

            // For each import not in MPS, remove it
            dependenciesInCloud.forEach { dependencyInCloud ->
                val modelImportedInCloud =
                    dependencyInCloud.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                if (modelImportedInCloud != null) {
                    val idProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.id
                    val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                    val matchingDependencyInMPS = dependenciesInCloud.firstOrNull { dependencyInMPS ->
                        val modelImportedInMPS =
                            dependencyInMPS.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
                        if (modelImportedInMPS == null) {
                            false
                        } else {
                            val modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(idProperty)
                            modelIDimportedInCloud == modelIDimportedInMPS
                        }
                    }
                    if (matchingDependencyInMPS == null) {
                        cloudModelNode.removeChild(dependencyInCloud)
                    }
                }
            }
        }
    }
}
