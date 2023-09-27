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

import jetbrains.mps.extapi.model.SModelDescriptorStub
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
import org.modelix.mps.sync.ICloudRepository
import org.modelix.mps.sync.util.copyPropertyIfNecessary
import org.modelix.mps.sync.util.replicateChild
import org.modelix.mps.sync.util.runInWriteActionIfNeeded
import java.util.UUID

// status: migrated, but needs some bugfixes
class ModelPropertiesSynchronizer(
    private val modelNodeId: Long,
    private val model: SModel,
    private val cloudRepository: ICloudRepository,
) {
    companion object {
        private val logger = mu.KotlinLogging.logger {}

        fun syncModelPropertiesToMPS(tree: ITree, model: SModel, modelNodeId: Long, cloudRepository: ICloudRepository) {
            syncUsedLanguagesAndDevKitsToMPS(tree, model, modelNodeId, cloudRepository)
            syncModelImportsToMPS(tree, model, modelNodeId, cloudRepository)

            try {
                val projects = ProjectManager.getInstance().openedProjects
                if (projects.isNotEmpty()) {
                    // TODO: VersionFixer is removed in MPS 2021.3. use ModuleDependencyVersions instead
//                    VersionFixer(projects.first(), model.module, true).updateImportVersions()
                }
            } catch (ex: Exception) {
                logger.error(ex) { "Failed to update language version after change in model ${model.name.value}" }
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
                    // TODO fixme. Problem SModelAsNode.wrap does not exist anymore in modelix...
                    // TODO it should be SModelAsNode instead of INode
                    // SModelAsNode.wrap(model);
                    val mpsModelNode: INode = null!!

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
                            // TODO fixme. We need the Concept class of DevkitDependency to do: concept/DevkitDependency/.getLanguage().getQualifiedName() and  concept/DevkitDependency/.getName()
                            if (dependencyInCloud.concept?.getLongName() == "fixme DevkitDependencyLanguageQualifiedName.fixme DevkitDependency.name") {
                                val repo = model.repository
                                val devKitUUID = dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
                                val devKit = repo.getModule(ModuleId.regular(UUID.fromString(devKitUUID))) as DevKit
                                val devKitModuleReference = devKit.moduleReference
                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // mpsModelNode.getElement().addDevKit(devKitModuleReference)

                                // TODO fixme. We need the Concept class of SingleLanguageDependency to do: concept/SingleLanguageDependency/.getLanguage().getQualifiedName() and  concept/SingleLanguageDependency/.getName()
                            } else if (dependencyInCloud.concept?.getLongName() == "TODO test if BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.getLongName() is the same as concept/SingleLanguageDependency/.getLanguage().getQualifiedName().concept/SingleLanguageDependency/.getName()") {
                                val repo = model.repository
                                val languageUUID =
                                    dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
                                val language =
                                    repo.getModule(ModuleId.regular(UUID.fromString(languageUUID))) as Language
                                val sLanguage = MetaAdapterFactory.getLanguage(language.moduleReference)

                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // mpsModelNode.getElement().addLanguageImport(sLanguage, Integer.parseInt(dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)))
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
                        // TODO fixme. org.modelix.mpsadapters.mps.DevKitDependencyAsNode does not exist in modelix
                        // TODO fixme. org.modelix.mpsadapters.mps.SingleLanguageDependencyAsNode does not exist in modelix

                        /*if (dependencyInMPS is DevKitDependencyAsNode) {
                            INode matchingDependencyInCloud = null;
                            foreach dependencyInCloud in dependenciesInCloud {
                                if (dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)) {
                                matchingDependencyInCloud = dependencyInCloud;
                            }
                            }
                            if (matchingDependencyInCloud == null) {
                                DefaultSModelDescriptor dsmd = ((DefaultSModelDescriptor) mpsModelNode.getElement());
                                DevKitDependencyAsNode depToRemove = (DevKitDependencyAsNode) dependencyInMPS;
                                SModuleReference moduleReference = ((SModuleReference) ReflectionUtil.readField(SingleLanguageDependencyAsNode.class, depToRemove, "moduleReference"));
                                SLanguage languageToRemove = MetaAdapterFactory.getLanguage(moduleReference);
                                dsmd.deleteLanguageId(languageToRemove);
                            }
                        } else if (dependencyInMPS is SingleLanguageDependencyAsNode) {
                            INode matchingDependencyInCloud = null;
                            foreach dependencyInCloud in dependenciesInCloud {
                                if (dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)) {
                                matchingDependencyInCloud = dependencyInCloud;
                            }
                            }
                            if (matchingDependencyInCloud == null) {
                                DefaultSModelDescriptor dsmd = ((DefaultSModelDescriptor) mpsModelNode.getElement());
                                SingleLanguageDependencyAsNode depToRemove = (SingleLanguageDependencyAsNode) dependencyInMPS;
                                SModuleReference moduleReference = depToRemove.getModuleReference();
                                SLanguage languageToRemove = MetaAdapterFactory.getLanguage(moduleReference);
                                dsmd.deleteLanguageId(languageToRemove);
                            }
                        } else {
                            throw new RuntimeException("Unknown dependency type: " + dependencyInMPS.getClass().getName());
                        }*/
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
                    // TODO fixme. Problem SModelAsNode.wrap does not exist anymore in modelix...
                    // TODO it should be SModelAsNode instead of INode
                    // SModelAsNode.wrap(model);
                    val mpsModelNode: INode = null!!
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
                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // mpsModelNode.getElement() as SModelDescriptorStub
                                val dsmd: SModelDescriptorStub = null!!

                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // dependencyInMPS.getElement().getReference();
                                val modelReferenceToRemove: SModelReference = null!!
                                dsmd.deleteModelImport(modelReferenceToRemove)
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
            // TODO fixme. Problem SModelAsNode.wrap does not exist anymore in modelix...
            val mpsModelNode: INode = null!! // SModelAsNode.wrap(model);
            val dependenciesInMPS: List<INode> =
                mpsModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).toList()

            // Then get the dependencies in the cloud
            val cloudModelNode = PNodeAdapter(modelNodeId, branch)
            val dependenciesInCloud =
                cloudModelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)

            // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
            dependenciesInMPS.forEach { dependencyInMPS ->
                // TODO fixme. org.modelix.mpsadapters.mps.DevKitDependencyAsNode does not exist in modelix
                // TODO fixme. org.modelix.mpsadapters.mps.SingleLanguageDependencyAsNode does not exist in modelix

                /*if (dependencyInMPS is DevKitDependencyAsNode) {
                    INode matchingDependencyInCloud = dependenciesInCloud . findFirst ({ ~dependencyInCloud => dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) })
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChild(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages, dependencyInMPS);
                    } else {
                        cloudModelNode.copyProperty(dependencyInMPS, BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name);
                    }
                } else if (dependencyInMPS is SingleLanguageDependencyAsNode) {
                    INode matchingDependencyInCloud = dependenciesInCloud . findFirst ({ ~dependencyInCloud => dependencyInMPS.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) == dependencyInCloud.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) })
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChild(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages, dependencyInMPS);
                    } else {
                        cloudModelNode.copyProperty(dependencyInMPS, BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name);
                        cloudModelNode.copyProperty(dependencyInMPS, BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version);
                    }
                } else {
                    throw RuntimeException ("Unknown dependency type: ${dependencyInMPS.getClass().getName()}");
                }*/
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
            // First get the dependencies in MPS. Model imports do not include implicit ones

            // TODO fixme. Problem SModelAsNode.wrap does not exist anymore in modelix...
            // TODO it should be SModelAsNode instead of INode
            val mpsModelNode: INode = null!! // SModelAsNode.wrap(model);
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
