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

package org.modelix.mps.sync.transformation.modelixToMps.incremental

import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.mps.util.runWriteActionCommandBlocking
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
import org.modelix.mps.sync.util.SyncBarrier
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModelImport
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.isModuleDependency
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelixTreeChangeVisitor(
    private val replicatedModel: ReplicatedModel,
    private val project: MPSProject,
    private val languageRepository: MPSLanguageRepository,
    private val isSynchronizing: SyncBarrier,
    private val nodeMap: MpsToModelixMap,
) : ITreeChangeVisitorEx {

    private val modelAccess = project.modelAccess

    private val nodeTransformer = NodeTransformer(modelAccess, nodeMap, languageRepository)
    private val modelTransformer = ModelTransformer(modelAccess, nodeMap)
    private val moduleTransformer = ModuleTransformer(project, nodeMap)

    override fun referenceChanged(nodeId: Long, role: String) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)!!
            var sReferenceLink: SReferenceLink? = null
            modelAccess.runReadBlocking {
                sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }
            }

            val iNode = getBranch().getNode(nodeId)
            var iReferenceLink: IReferenceLink?
            var targetINode: INode? = null
            getBranch().runRead {
                iReferenceLink = iNode.getReferenceLinks().find { it.getSimpleName() == role }
                targetINode = iReferenceLink?.let { iNode.getReferenceTarget(it) }
            }
            val targetSNode = targetINode?.let { nodeMap.getNode(it.nodeIdAsLong()) }

            modelAccess.runWriteActionCommandBlocking {
                sNode.setReferenceTarget(sReferenceLink!!, targetSNode)
            }
        }
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)!!
            var sProperty: SProperty? = null
            modelAccess.runReadBlocking {
                sProperty = sNode.concept.properties.find { it.name == role }
            }

            val iNode = getBranch().getNode(nodeId)
            val iProperty = PropertyFromName(role)
            var value: String? = null
            getBranch().runRead { value = iNode.getPropertyValue(iProperty) }

            modelAccess.runWriteActionCommandBlocking {
                sNode.setProperty(sProperty!!, value)
            }
        }
    }

    override fun nodeRemoved(nodeId: Long) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                modelAccess.runWriteActionCommandBlocking {
                    it.delete()
                    nodeMap.remove(nodeId)
                }
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelAccess.runWriteActionCommandBlocking {
                    ModelDeleteHelper(sModel).delete()
                    nodeMap.remove(nodeId)
                }
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                modelAccess.runWriteActionCommandBlocking {
                    sModule.models.forEach { model ->
                        val modelNodeId = nodeMap[model]
                        ModelDeleteHelper(model).delete()
                        modelNodeId?.let { nodeMap.remove(it) }
                    }
                    ModuleDeleteHelper(project).deleteModules(listOf(sModule), false, true)
                    nodeMap.remove(nodeId)
                }
            }
        }
    }

    override fun nodeAdded(nodeId: Long) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val mpsActionRunner = modelAccess::runWriteActionCommandBlocking
            val iNode = getBranch().getNode(nodeId)

            getBranch().runRead {
                if (iNode.isModule()) {
                    moduleTransformer.transformToModule(iNode)
                } else if (iNode.isModuleDependency()) {
                    val moduleNodeId = iNode.getModule()?.nodeIdAsLong()
                    val parentModule = nodeMap.getModule(moduleNodeId)!!
                    require(parentModule is AbstractModule) { "Parent Module ($moduleNodeId) of INode (${iNode.nodeIdAsLong()}) is not an AbstractModule." }
                    moduleTransformer.transformModuleDependency(iNode, parentModule, mpsActionRunner)
                } else if (iNode.isModel()) {
                    modelTransformer.transformToModel(iNode, mpsActionRunner)
                } else if (iNode.isModelImport()) {
                    modelTransformer.transformModelImport(iNode)
                } else if (iNode.isSingleLanguageDependency()) {
                    nodeTransformer.transformLanguageDependency(iNode, mpsActionRunner, true)
                } else if (iNode.isDevKitDependency()) {
                    nodeTransformer.transformDevKitDependency(iNode, mpsActionRunner, true)
                } else {
                    nodeTransformer.transformToNode(iNode)
                }
            }
        }
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val mpsActionRunner = modelAccess::runWriteActionCommandBlocking

            modelTransformer.resolveModelImports(languageRepository.repository, mpsActionRunner)
            modelTransformer.clearResolvableModelImports()

            nodeTransformer.resolveReferences(mpsActionRunner)
            nodeTransformer.clearResolvableReferences()
        }
    }

    override fun containmentChanged(nodeId: Long) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val iNode = getBranch().getNode(nodeId)
            var newParentId: Long? = null
            getBranch().runRead {
                newParentId = iNode.parent?.nodeIdAsLong()
            }

            val sNode = nodeMap.getNode(nodeId)
            if (sNode != null) {
                val newParentNode = nodeMap.getNode(newParentId)
                if (newParentNode != null) {
                    var containment: SContainmentLink? = null
                    getBranch().runRead {
                        containment =
                            newParentNode.concept.containmentLinks.find {
                                it.name == iNode.getContainmentLink()!!.getSimpleName()
                            }
                    }
                    modelAccess.runWriteActionCommandBlocking {
                        // remove from old parent
                        sNode.parent?.removeChild(sNode)
                        sNode.model?.removeRootNode(sNode)

                        // add to new parent
                        newParentNode.addChild(containment!!, sNode)
                    }
                }

                val newParentModel = nodeMap.getModel(newParentId)
                if (newParentModel != null) {
                    modelAccess.runWriteActionCommandBlocking {
                        // remove from old parent
                        sNode.model?.removeRootNode(sNode)
                        sNode.parent?.removeChild(sNode)

                        // add to new parent
                        newParentModel.addRootNode(sNode)
                    }
                }
            } else {
                val sModel = nodeMap.getModel(nodeId)
                val newParentModule = nodeMap.getModule(newParentId)
                if (sModel != null && newParentModule != null) {
                    require(sModel is SModelBase) { "Model ${sModel.modelId} is not an SModelBase" }

                    // remove from old parent
                    val oldParentModule = sModel.module
                    require(oldParentModule is SModuleBase) { "Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase" }
                    modelAccess.runWriteActionCommandBlocking {
                        oldParentModule.unregisterModel(sModel)
                        sModel.module = null
                    }

                    // add to new parent
                    require(newParentModule is SModuleBase) { "New parent Module ${newParentModule.moduleId} is not an SModuleBase" }
                    modelAccess.runWriteActionCommandBlocking {
                        newParentModule.registerModel(sModel)
                    }
                }
            }
        }
    }

    private fun handleThrowable(t: Throwable) = t.printStackTrace()

    private fun getBranch() = replicatedModel.getBranch()
}
