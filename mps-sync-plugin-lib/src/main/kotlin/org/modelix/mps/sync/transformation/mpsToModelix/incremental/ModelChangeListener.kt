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

package org.modelix.mps.sync.transformation.mpsToModelix.incremental

import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.runIfAlone
import java.util.concurrent.atomic.AtomicReference

// TODO some methods need some testing
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelChangeListener(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val nodeChangeListener: NodeChangeListener,
    private val isSynchronizing: AtomicReference<Boolean>,
) : SModelListener {

    override fun importAdded(event: SModelImportEvent) {
        isSynchronizing.runIfAlone {
            // TODO might not work, we have to test it
            val modelixId = nodeMap[event.model]!!
            val mpsModelReference = event.modelUID
            val targetModel = mpsModelReference.resolve(event.model.repository)

            val modelImportsLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports
            val modelReferenceConcept = BuiltinLanguages.MPSRepositoryConcepts.ModelReference

            branch.runWriteT {
                val cloudParentNode = branch.getNode(modelixId)
                val cloudModelReference = cloudParentNode.addNewChild(modelImportsLink, -1, modelReferenceConcept)

                nodeMap.put(mpsModelReference, cloudModelReference.nodeIdAsLong())

                // warning: might be fragile, because we just sync the "model" reference, but no other fields
                val targetModelModelixId = nodeMap[targetModel]!!
                val cloudTargetModel = branch.getNode(targetModelModelixId)
                cloudModelReference.setReferenceTarget(
                    BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model,
                    cloudTargetModel,
                )
            }
        }
    }

    override fun importRemoved(event: SModelImportEvent) {
        isSynchronizing.runIfAlone {
            // TODO might not work, we have to test it
            // TODO deduplicate implementation
            val parentNodeId = nodeMap[event.model]!!
            val nodeId = nodeMap[event.modelUID]!!
            branch.runWriteT {
                val cloudParentNode = branch.getNode(parentNodeId)
                val cloudChildNode = branch.getNode(nodeId)
                cloudParentNode.removeChild(cloudChildNode)
            }
        }
    }

    override fun languageAdded(event: SModelLanguageEvent) {
        isSynchronizing.runIfAlone {
            val modelixId = nodeMap[event.model]!!

            val language = event.eventLanguage
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
                    event.model.module.getUsedLanguageVersion(language).toString(),
                )
            }
        }
    }

    override fun languageRemoved(event: SModelLanguageEvent) {
        isSynchronizing.runIfAlone {
            val modelixId = nodeMap[event.model]!!

            val languageModuleReference = event.eventLanguage.sourceModuleReference
            val languageModuleReferenceModelixId = nodeMap[languageModuleReference]!!

            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                val cloudLanguageModuleReference = branch.getNode(languageModuleReferenceModelixId)
                cloudNode.removeChild(cloudLanguageModuleReference)
            }
        }
    }

    override fun devkitAdded(event: SModelDevKitEvent) {
        isSynchronizing.runIfAlone {
            val modelixId = nodeMap[event.model]!!

            val repository = event.model.repository
            val devKitModuleReference = event.devkitNamespace
            val devKitModuleId = devKitModuleReference.moduleId
            val devKitModule = repository.getModule(devKitModuleId)

            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                val cloudDevKitDependency =
                    cloudNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)

                // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ITreeToSTreeTransformer, when downloading DevKits from the cloud)
                nodeMap.put(devKitModuleReference, cloudDevKitDependency.nodeIdAsLong())

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
    }

    override fun devkitRemoved(event: SModelDevKitEvent) {
        isSynchronizing.runIfAlone {
            val modelixId = nodeMap[event.model]!!

            val devKitModuleReference = event.devkitNamespace
            val devKitModuleReferenceModelixId = nodeMap[devKitModuleReference]!!

            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                val cloudDevKitModuleReference = branch.getNode(devKitModuleReferenceModelixId)
                cloudNode.removeChild(cloudDevKitModuleReference)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun rootAdded(event: SModelRootEvent) {
        isSynchronizing.runIfAlone {
            // TODO deduplicate implementation
            val parentNodeId = nodeMap[event.model]!!
            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes

            val mpsChild = event.root
            val mpsConcept = mpsChild.concept

            val nodeId = nodeMap[mpsChild]
            val childExists = nodeId != null
            branch.runWriteT { transaction ->
                if (childExists) {
                    transaction.moveChild(parentNodeId, childLink.getSimpleName(), -1, nodeId!!)
                } else {
                    val cloudParentNode = branch.getNode(parentNodeId)
                    val cloudChildNode = cloudParentNode.addNewChild(childLink, -1, MPSConcept(mpsConcept))

                    // save the modelix ID and the SNode in the map
                    nodeMap.put(mpsChild, cloudChildNode.nodeIdAsLong())

                    synchronizeNodeToCloud(mpsConcept, mpsChild, cloudChildNode)
                }
            }
        }
    }

    override fun modelRenamed(event: SModelRenamedEvent) {
        isSynchronizing.runIfAlone {
            // TODO deduplicate implementation
            val modelixId = nodeMap[event.model]!!
            branch.runWriteT {
                val cloudNode = branch.getNode(modelixId)
                cloudNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, event.newName)
            }
        }
    }

    override fun beforeModelDisposed(model: SModel) {
        model.removeChangeListener(nodeChangeListener)
        (model as? SModelInternal)?.removeModelListener(this)
    }

    @Deprecated("Deprecated in Java")
    override fun rootRemoved(event: SModelRootEvent) {
        // duplicate of SNodeChangeListener.nodeRemoved
    }

    override fun getPriority(): SModelListener.SModelListenerPriority = SModelListener.SModelListenerPriority.CLIENT

    override fun propertyChanged(event: SModelPropertyEvent) {
        // duplicate of SNodeChangeListener.propertyChanged
    }

    override fun childAdded(event: SModelChildEvent) {
        // duplicate of SNodeChangeListener.childAdded
    }

    override fun childRemoved(event: SModelChildEvent) {
        // duplicate of SNodeChangeListener.nodeRemoved
    }

    override fun referenceAdded(event: SModelReferenceEvent) {
        // duplicate of SNodeChangeListener.referenceChanged
    }

    override fun referenceRemoved(event: SModelReferenceEvent) {
        // duplicate of SNodeChangeListener.referenceChanged
    }

    override fun beforeChildRemoved(event: SModelChildEvent) {}
    override fun beforeRootRemoved(event: SModelRootEvent) {}
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}
    override fun modelSaved(model: SModel) {}
    override fun modelLoadingStateChanged(model: SModel?, state: ModelLoadingState) {}

    // TODO deduplicate implementation
    private fun synchronizeNodeToCloud(
        mpsConcept: SConcept,
        mpsNode: SNode,
        cloudNode: INode,
    ) {
        // synchronize properties
        mpsConcept.properties.forEach {
            val mpsValue = mpsNode.getProperty(it)
            val modelixProperty = PropertyFromName(it.name)
            cloudNode.setPropertyValue(modelixProperty, mpsValue)
        }

        // synchronize references
        mpsConcept.referenceLinks.forEach {
            val mpsTargetNode = mpsNode.getReferenceTarget(it)!!
            val targetNodeId = nodeMap[mpsTargetNode]!!

            val modelixReferenceLink = MPSReferenceLink(it)
            val cloudTargetNode = branch.getNode(targetNodeId)

            cloudNode.setReferenceTarget(modelixReferenceLink, cloudTargetNode)
        }

        // synchronize children
        mpsConcept.containmentLinks.forEach { containmentLink ->
            mpsNode.getChildren(containmentLink).forEach { mpsChild ->
                val childLink = MPSChildLink(containmentLink)
                val mpsChildConcept = mpsChild.concept
                val cloudChildNode = cloudNode.addNewChild(childLink, -1, MPSConcept(mpsChildConcept))

                // save the modelix ID and the SNode in the map
                nodeMap.put(mpsChild, cloudChildNode.nodeIdAsLong())

                synchronizeNodeToCloud(mpsChildConcept, mpsChild, cloudChildNode)
            }
        }
    }
}