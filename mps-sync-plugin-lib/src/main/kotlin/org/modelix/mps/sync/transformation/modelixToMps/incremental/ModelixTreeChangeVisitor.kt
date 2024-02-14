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

import com.intellij.openapi.diagnostic.logger
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

    private val logger = logger<ModelixTreeChangeVisitor>()

    private val nodeTransformer = NodeTransformer(nodeMap, syncQueue, languageRepository)
    private val modelTransformer = ModelTransformer(nodeMap, syncQueue)
    private val moduleTransformer = ModuleTransformer(nodeMap, syncQueue, project)

    override fun referenceChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val sNode = nodeMap.getNode(nodeId)
            if (sNode == null) {
                logger.error("Node ($nodeId) is not mapped to MPS yet.")
                return@enqueue null
            }

            val sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }
            if (sReferenceLink == null) {
                logger.error("Node ($nodeId)'s concept (${sNode.concept.name}) does not have reference link called $role.")
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            val iReferenceLink = iNode.getReferenceLinks().find { it.getSimpleName() == role }
            val targetINode = iReferenceLink?.let { iNode.getReferenceTarget(it) }
            val targetSNode = targetINode?.let { nodeMap.getNode(it.nodeIdAsLong()) }

            val oldValue = sNode.getReferenceTarget(sReferenceLink)
            if (oldValue != targetSNode) {
                sNode.setReferenceTarget(sReferenceLink, targetSNode)
            }

            null
        }
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val sNode = nodeMap.getNode(nodeId)
            if (sNode == null) {
                logger.error("Node ($nodeId) is not mapped to MPS yet.")
                return@enqueue null
            }

            val sProperty = sNode.concept.properties.find { it.name == role }
            if (sProperty == null) {
                logger.error("Node ($nodeId)'s concept (${sNode.concept.name}) does not have property called $role.")
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            val iProperty = PropertyFromName(role)
            val value = iNode.getPropertyValue(iProperty)

            val oldValue = sNode.getProperty(sProperty)
            if (oldValue != value) {
                sNode.setProperty(sProperty, value)
            }

            null
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
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                ModelDeleteHelper(sModel).delete()
                nodeMap.remove(nodeId)
                return@enqueue null
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
                return@enqueue null
            }

            val isMapped = nodeMap.isMappedToMps(nodeId)
            // if isMapped == false, then we missed a possible removal case
            logger.error("Node ($nodeId) was not removed from MPS, because it was not mapped yet (isMapped=$isMapped).")

            null
        }
    }

    override fun nodeAdded(nodeId: Long) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val isMapped = nodeMap.isMappedToMps(nodeId)
            if (isMapped) {
                logger.error("Node ($nodeId) is already mapped to MPS.")
                return@enqueue null
            }

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

            null
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

            null
        }
    }

    override fun containmentChanged(nodeId: Long) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val iNode = getNode(nodeId)
            val newParent = iNode.parent
            if (newParent == null) {
                logger.error("Node ($nodeId)'s new parent is null.")
                return@enqueue null
            }
            val newParentId = newParent.nodeIdAsLong()

            val containmentLink = iNode.getContainmentLink()
            if (containmentLink == null) {
                logger.error("Node ($nodeId)'s containment link is null.")
                return@enqueue null
            }

            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                // node moved to a new parent node
                val newParentNode = nodeMap.getNode(newParentId)
                newParentNode?.let {
                    val oldParent = sNode.parent
                    if (oldParent == newParentNode) {
                        return@enqueue null
                    }

                    val containmentLinkName = containmentLink.getSimpleName()
                    val containment = newParentNode.concept.containmentLinks.find { it.name == containmentLinkName }
                    if (containment == null) {
                        logger.error("Node ($nodeId)'s concept (${sNode.concept.name}) does not have containment link called $containmentLinkName.")
                        return@enqueue null
                    }

                    // remove from old parent
                    oldParent?.removeChild(sNode)
                    sNode.model?.removeRootNode(sNode)

                    // add to new parent
                    newParentNode.addChild(containment, sNode)
                    return@enqueue null
                }

                // node moved to a new parent model
                val newParentModel = nodeMap.getModel(newParentId)
                newParentModel?.let {
                    val parentModel = sNode.model
                    if (parentModel == newParentModel) {
                        return@enqueue null
                    }

                    // remove from old parent
                    parentModel?.removeRootNode(sNode)
                    sNode.parent?.removeChild(sNode)

                    // add to new parent
                    newParentModel.addRootNode(sNode)
                    return@enqueue null
                }

                logger.error("Node ($nodeId) was neither moved to a new parent node nor to a new parent model, because Modelix Node $newParentId was not mapped to MPS yet.")
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                val newParentModule = nodeMap.getModule(newParentId)
                if (newParentModule == null) {
                    logger.error("Model ($nodeId) was not moved to a new parent module, because  Modelix Node $newParentId was not mapped to MPS yet.")
                    return@enqueue null
                }

                val oldParentModule = sModel.module
                if (oldParentModule == newParentModule) {
                    return@enqueue null
                }

                // remove from old parent
                require(oldParentModule is SModuleBase) { "Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase" }
                require(sModel is SModelBase) { "Model ${sModel.modelId} is not an SModelBase" }
                oldParentModule.unregisterModel(sModel)
                sModel.module = null

                // add to new parent
                require(newParentModule is SModuleBase) { "New parent Module ${newParentModule.moduleId} is not an SModuleBase" }
                newParentModule.registerModel(sModel)
                return@enqueue null
            }

            val isMapped = nodeMap.isMappedToMps(newParentId)
            // if isMapped == false, then we missed a possible new parent case
            logger.error("Node ($nodeId) was not moved to a new parent, because Modelix Node $newParentId was not mapped to MPS yet (isMapped=$isMapped).")

            null
        }
    }

    private fun getNode(nodeId: Long) = replicatedModel.getBranch().getNode(nodeId)
}
