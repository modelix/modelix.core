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
import jetbrains.mps.lang.migration.runtime.base.VersionFixer
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
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
                    VersionFixer(projects.first(), model.module, true).updateImportVersions()
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

                    // TODO instead of "usedLanguages" it must be link/Model: usedLanguages/.getName()
                    val dependenciesInMPS: List<INode> = mpsModelNode.getChildren("usedLanguages").toList()

                    // Then get the dependencies in the cloud
                    val branch = cloudRepository.getBranch()
                    val cloudModelNode = PNodeAdapter(modelNodeId, branch)
                    // TODO instead of "usedLanguages" it must be link/Model: usedLanguages/.getName()
                    val dependenciesInCloud = cloudModelNode.getChildren("usedLanguages")

                    // For each import in the cloud add it if not present in MPS or otherwise ensure all properties are the same
                    dependenciesInCloud.forEach { dependencyInCloud ->
                        val matchingDependencyInMPS = dependenciesInMPS.firstOrNull { dependencyInMPS ->
                            // TODO instead of "uuid" it must be property/SingleLanguageDependency : uuid/.getName()
                            val property = PropertyFromName("uuid")
                            dependencyInCloud.getPropertyValue(property) == dependencyInMPS.getPropertyValue(property)
                        }
                        if (matchingDependencyInMPS == null) {
                            // TODO fixme. We need the Concept class of DevkitDependency to do: concept/DevkitDependency/.getLanguage().getQualifiedName() and  concept/DevkitDependency/.getName()
                            if (dependencyInCloud.concept?.getLongName() == "fixme DevkitDependencyLanguageQualifiedName.fixme DevkitDependency.name") {
                                val repo = model.repository
                                // TODO instead of "uuid" it must be property/DevkitDependency : uuid/.getName()
                                val property = PropertyFromName("uuid")
                                val devKitUUID = dependencyInCloud.getPropertyValue(property)
                                val devKit = repo.getModule(ModuleId.regular(UUID.fromString(devKitUUID))) as DevKit
                                val devKitModuleReference = devKit.moduleReference
                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // mpsModelNode.getElement().addDevKit(devKitModuleReference)

                                // TODO fixme. We need the Concept class of SingleLanguageDependency to do: concept/SingleLanguageDependency/.getLanguage().getQualifiedName() and  concept/SingleLanguageDependency/.getName()
                            } else if (dependencyInCloud.concept?.getLongName() == "fixme SingleLanguageDependency.fixme SingleLanguageDependency.name") {
                                val repo = model.repository
                                // TODO instead of "uuid" it must be property/SingleLanguageDependency : uuid/.getName()
                                val property = PropertyFromName("uuid")
                                val languageUUID = dependencyInCloud.getPropertyValue(property)
                                val language =
                                    repo.getModule(ModuleId.regular(UUID.fromString(languageUUID))) as Language
                                val sLanguage = MetaAdapterFactory.getLanguage(language.moduleReference)

                                // TODO instead of "version" it must be property/SingleLanguageDependency : version/.getName()
                                val versionProperty = PropertyFromName("version")
                                dependencyInCloud.getPropertyValue(versionProperty)

                                // TODO fixme. getElement() does not exist, because mpsModelNode should be SModelAsNode...
                                // getElement() is supposed to return an SModel
                                // mpsModelNode.getElement().addLanguageImport(sLanguage, Integer.parseInt(versionProperty))
                            } else {
                                throw UnsupportedOperationException("Unknown dependency with concept ${dependencyInCloud.concept?.getLongName()}")
                            }
                        } else {
                            // We use this method to avoid using set, if it is not strictly necessary, which may be not supported

                            // TODO instead of "name" it must be property/SingleLanguageDependency : name/.getName()
                            val nameProperty = PropertyFromName("name")
                            matchingDependencyInMPS.copyPropertyIfNecessary(dependencyInCloud, nameProperty)

                            // TODO instead of "version" it must be property/SingleLanguageDependency : version/.getName()
                            val versionProperty = PropertyFromName("version")
                            matchingDependencyInMPS.copyPropertyIfNecessary(dependencyInCloud, versionProperty)
                        }
                    }

                    // For each import not in Cloud remove it
                    dependenciesInMPS.forEach { dependencyInMPS ->
                        // TODO fixme. org.modelix.mpsadapters.mps.DevKitDependencyAsNode does not exist in modelix
                        // TODO fixme. org.modelix.mpsadapters.mps.SingleLanguageDependencyAsNode does not exist in modelix

                        /*if (dependencyInMPS is DevKitDependencyAsNode) {
                            INode matchingDependencyInCloud = null;
                            foreach dependencyInCloud in dependenciesInCloud {
                                if (dependencyInMPS.getPropertyValue(property/DevkitDependency : uuid/.getName()) :eq: dependencyInCloud.getPropertyValue(property/DevkitDependency : uuid/.getName())) {
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
                                if (dependencyInMPS.getPropertyValue(property/SingleLanguageDependency : uuid/.getName()) :eq: dependencyInCloud.getPropertyValue(property/SingleLanguageDependency : uuid/.getName())) {
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
                    // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
                    val dependenciesInMPS = mpsModelNode.getChildren("modelImports")

                    // Then get the dependencies in the cloud
                    val branch = cloudRepository.getBranch()
                    val cloudModelNode = PNodeAdapter(modelNodeId, branch)
                    // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
                    val dependenciesInCloud = cloudModelNode.getChildren("modelImports")

                    // For each import in Cloud add it if not present in MPS or otherwise ensure all properties are the same
                    dependenciesInCloud.forEach { dependencyInCloud ->
                        // TODO instead of "model" it must be link/ModelReference: model/.getName()
                        val modelImportedInCloud = dependencyInCloud.getReferenceTarget("model")
                        if (modelImportedInCloud != null) {
                            // TODO instead of "id" it must be property/Model: id/.getName()
                            val idProperty = PropertyFromName("id")
                            val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                            val matchingDependencyInMps = dependenciesInMPS.firstOrNull { dependencyInMPS ->
                                // TODO instead of "model" it must be link/ModelReference: model/.getName()
                                val modelImportedInMPS = dependencyInMPS.getReferenceTarget("model")
                                if (modelImportedInMPS == null) {
                                    false
                                } else {
                                    val modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(idProperty)
                                    modelIDimportedInCloud == modelIDimportedInMPS
                                }
                            }
                            if (matchingDependencyInMps == null) {
                                // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
                                mpsModelNode.replicateChild("modelImports", dependencyInCloud)
                            } else {
                                // no properties to set here
                            }
                        }
                    }

                    // For each import not in Cloud remove it
                    dependenciesInMPS.forEach { dependencyInMPS ->
                        // TODO instead of "model" it must be link/ModelReference: model/.getName()
                        val modelImportedInMPS = dependencyInMPS.getReferenceTarget("model")
                        if (modelImportedInMPS != null) {
                            // TODO instead of "id" it must be property/Model: id/.getName()
                            val idProperty = PropertyFromName("id")
                            val modelIDimportedInMPS = modelImportedInMPS.getPropertyValue(idProperty)
                            val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                                // TODO instead of "model" it must be link/ModelReference: model/.getName()
                                val modelImportedInCloud = dependencyInCloud.getReferenceTarget("model")
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

            // TODO instead of "usedLanguages" it must be link/Model: usedLanguages/.getName()
            val dependenciesInMPS: List<INode> = mpsModelNode.getChildren("usedLanguages").toList()

            // Then get the dependencies in the cloud
            val cloudModelNode = PNodeAdapter(modelNodeId, branch)
            // TODO instead of "usedLanguages" it must be link/Model: usedLanguages/.getName()
            val dependenciesInCloud = cloudModelNode.getChildren("usedLanguages")

            // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
            dependenciesInMPS.forEach { dependencyInMPS ->
                // TODO fixme. org.modelix.mpsadapters.mps.DevKitDependencyAsNode does not exist in modelix
                // TODO fixme. org.modelix.mpsadapters.mps.SingleLanguageDependencyAsNode does not exist in modelix

                /*if (dependencyInMPS is DevKitDependencyAsNode) {
                    INode matchingDependencyInCloud = dependenciesInCloud . findFirst ({ ~dependencyInCloud => dependencyInMPS.getPropertyValue(property/DevkitDependency : uuid/.getName()) :eq: dependencyInCloud.getPropertyValue(property/DevkitDependency : uuid/.getName()); });
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChildx(link / Model : usedLanguages/.getName(), dependencyInMPS);
                    } else {
                        cloudModelNode.copyPropertyx(dependencyInMPS, property / DevkitDependency : name/);
                    }
                } else if (dependencyInMPS is SingleLanguageDependencyAsNode) {
                    INode matchingDependencyInCloud = dependenciesInCloud . findFirst ({ ~dependencyInCloud => dependencyInMPS.getPropertyValue(property/SingleLanguageDependency : uuid/.getName()) :eq: dependencyInCloud.getPropertyValue(property/SingleLanguageDependency : uuid/.getName()); });
                    if (matchingDependencyInCloud == null) {
                        cloudModelNode.replicateChildx(link / Model : usedLanguages/.getName(), dependencyInMPS);
                    } else {
                        cloudModelNode.copyPropertyx(dependencyInMPS, property / SingleLanguageDependency : name/);
                        cloudModelNode.copyPropertyx(dependencyInMPS, property / SingleLanguageDependency : version/);
                    }
                } else {
                    throw new RuntimeException ("Unknown dependency type: " + dependencyInMPS.getClass().getName());
                }*/
            }

            // For each import not in MPS, remove it
            dependenciesInCloud.forEach { dependencyInCloud ->
                val matchingDependencyInMPS = dependenciesInCloud.firstOrNull { dependencyInMPS ->
                    // TODO instead of "uuid" it must be property/SingleLanguageDependency : uuid/.getName()
                    val property = PropertyFromName("uuid")
                    dependencyInCloud.getPropertyValue(property) == dependencyInMPS.getPropertyValue(property)
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
            // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
            val dependenciesInMPS = mpsModelNode.getChildren("modelImports")

            // Then get the dependencies in the cloud
            val cloudModelNode = PNodeAdapter(modelNodeId, branch)
            // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
            val dependenciesInCloud = cloudModelNode.getChildren("modelImports")

            // For each import in MPS, add it if not present in the cloud, or otherwise ensure all properties are the same
            dependenciesInMPS.forEach { dependencyInMPS ->
                // TODO instead of "model" it must be link/ModelReference: model/.getName()
                val modelImportedInMps = dependencyInMPS.getReferenceTarget("model")
                if (modelImportedInMps != null) {
                    // TODO instead of "id" it must be property/Model: id/.getName()
                    val idProperty = PropertyFromName("id")
                    val modelIDimportedInMPS = modelImportedInMps.getPropertyValue(idProperty)
                    val matchingDependencyInCloud = dependenciesInCloud.firstOrNull { dependencyInCloud ->
                        // TODO instead of "model" it must be link/ModelReference: model/.getName()
                        val modelImportedInCloud = dependencyInCloud.getReferenceTarget("model")
                        if (modelImportedInCloud == null) {
                            false
                        } else {
                            val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                            modelIDimportedInMPS == modelIDimportedInCloud
                        }
                    }
                    if (matchingDependencyInCloud == null) {
                        // TODO instead of "modelImports" it must be link/Model: modelImports/.getName()
                        cloudModelNode.replicateChild("modelImports", dependencyInMPS)
                    } else {
                        // no properties to set here
                    }
                }
            }

            // For each import not in MPS, remove it
            dependenciesInCloud.forEach { dependencyInCloud ->
                // TODO instead of "model" it must be link/ModelReference: model/.getName()
                val modelImportedInCloud = dependencyInCloud.getReferenceTarget("model")
                if (modelImportedInCloud != null) {
                    // TODO instead of "id" it must be property/Model: id/.getName()
                    val idProperty = PropertyFromName("id")
                    val modelIDimportedInCloud = modelImportedInCloud.getPropertyValue(idProperty)
                    val matchingDependencyInMPS = dependenciesInCloud.firstOrNull { dependencyInMPS ->
                        // TODO instead of "model" it must be link/ModelReference: model/.getName()
                        val modelImportedInMPS = dependencyInMPS.getReferenceTarget("model")
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
