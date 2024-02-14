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
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.tasks.InspectionMode
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
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
    languageRepository: MPSLanguageRepository,
    private val nodeMap: MpsToModelixMap,
    private val syncQueue: SyncQueue,
) : ITreeChangeVisitorEx {

    private val nodeTransformer = NodeTransformer(nodeMap, syncQueue, languageRepository)
    private val modelTransformer = ModelTransformer(nodeMap, syncQueue)
    private val moduleTransformer = ModuleTransformer(nodeMap, syncQueue, project)

    override fun referenceChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val sNode = nodeMap.getNode(nodeId)!!
            val sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }

            val iNode = getNode(nodeId)
            val iReferenceLink = iNode.getReferenceLinks().find { it.getSimpleName() == role }
            val targetINode = iReferenceLink?.let { iNode.getReferenceTarget(it) }
            val targetSNode = targetINode?.let { nodeMap.getNode(it.nodeIdAsLong()) }

            sNode.setReferenceTarget(sReferenceLink!!, targetSNode)
        }
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val sNode = nodeMap.getNode(nodeId)!!
            val sProperty = sNode.concept.properties.find { it.name == role }

            val iNode = getNode(nodeId)
            val iProperty = PropertyFromName(role)
            val value = iNode.getPropertyValue(iProperty)

            sNode.setProperty(sProperty!!, value)
        }
    }

    override fun nodeRemoved(nodeId: Long) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                it.delete()
                nodeMap.remove(nodeId)
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                ModelDeleteHelper(sModel).delete()
                nodeMap.remove(nodeId)
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
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

    override fun nodeAdded(nodeId: Long) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val iNode = getNode(nodeId)
            if (iNode.isModule()) {
                moduleTransformer.transformToModule(iNode)
            } else if (iNode.isModuleDependency()) {
                val moduleNodeId = iNode.getModule()?.nodeIdAsLong()
                val parentModule = nodeMap.getModule(moduleNodeId)!!
                require(parentModule is AbstractModule) { "Parent Module ($moduleNodeId) of INode (${iNode.nodeIdAsLong()}) is not an AbstractModule." }
                moduleTransformer.transformModuleDependency(iNode, parentModule)
            } else if (iNode.isModel()) {
                modelTransformer.transformToModel(iNode)
            } else if (iNode.isModelImport()) {
                modelTransformer.transformModelImport(iNode)
            } else if (iNode.isSingleLanguageDependency()) {
                nodeTransformer.transformLanguageDependency(iNode)
            } else if (iNode.isDevKitDependency()) {
                nodeTransformer.transformDevKitDependency(iNode)
            } else {
                nodeTransformer.transformToNode(iNode)
            }
        }
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            modelTransformer.resolveModelImports(project.repository)
            modelTransformer.clearResolvableModelImports()

            nodeTransformer.resolveReferences()
            nodeTransformer.clearResolvableReferences()
        }
    }

    override fun containmentChanged(nodeId: Long) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val iNode = getNode(nodeId)
            val newParentId = iNode.parent?.nodeIdAsLong()

            val sNode = nodeMap.getNode(nodeId)
            if (sNode != null) {
                val newParentNode = nodeMap.getNode(newParentId)
                if (newParentNode != null) {
                    val containment = newParentNode.concept.containmentLinks.find {
                        it.name == iNode.getContainmentLink()!!.getSimpleName()
                    }
                    // remove from old parent
                    sNode.parent?.removeChild(sNode)
                    sNode.model?.removeRootNode(sNode)

                    // add to new parent
                    newParentNode.addChild(containment!!, sNode)
                }

                val newParentModel = nodeMap.getModel(newParentId)
                if (newParentModel != null) {
                    // remove from old parent
                    sNode.model?.removeRootNode(sNode)
                    sNode.parent?.removeChild(sNode)

                    // add to new parent
                    newParentModel.addRootNode(sNode)
                }
            } else {
                val sModel = nodeMap.getModel(nodeId)
                val newParentModule = nodeMap.getModule(newParentId)
                if (sModel != null && newParentModule != null) {
                    require(sModel is SModelBase) { "Model ${sModel.modelId} is not an SModelBase" }

                    // remove from old parent
                    val oldParentModule = sModel.module
                    require(oldParentModule is SModuleBase) { "Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase" }
                    oldParentModule.unregisterModel(sModel)
                    sModel.module = null

                    // add to new parent
                    require(newParentModule is SModuleBase) { "New parent Module ${newParentModule.moduleId} is not an SModuleBase" }
                    newParentModule.registerModel(sModel)
                }
            }
        }
    }

    private fun getNode(nodeId: Long) = replicatedModel.getBranch().getNode(nodeId)
}
