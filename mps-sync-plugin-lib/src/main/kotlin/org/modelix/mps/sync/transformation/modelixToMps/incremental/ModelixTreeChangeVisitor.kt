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
import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.refactoring.Renamer
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.util.ModelRenameHelper
import org.modelix.mps.sync.tasks.InspectionMode
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
import org.modelix.mps.sync.util.BooleanUtil
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModelImport
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.isModuleDependency
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong
import java.text.ParseException

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
            val iNode = getNode(nodeId)
            val iProperty = PropertyFromName(role)
            val newValue = iNode.getPropertyValue(iProperty)

            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                nodePropertyChanged(sNode, role, nodeId, newValue)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelPropertyChanged(sModel, role, newValue, nodeId)
                return@enqueue null
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                modulePropertyChanged(role, nodeId, sModule, newValue)
                return@enqueue null
            }

            val isMapped = nodeMap.isMappedToMps(nodeId)
            // if isMapped == true, then we missed a possible removal case
            logger.info("Property $role of Node ($nodeId) was not set in MPS, because it might not exist yet (isMapped=$isMapped).")

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
                nodeDeleted(it, nodeId)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelDeleted(sModel, nodeId)
                return@enqueue null
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                moduleDeleted(sModule, nodeId)
                return@enqueue null
            }

            val isMapped = nodeMap.isMappedToMps(nodeId)
            // if isMapped == true, then we missed a possible removal case
            logger.info("Node ($nodeId) was not removed from MPS, because it might have been already removed (isMapped=$isMapped).")

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

    /**
     * TODO rethink if we have to limit childrenChanged operation further
     * it is expected to be called after the nodeAdded methods and thereby we have to resolve the modelImports and references
     * However, this method can be also called before/after the nodeDeleted operation. Where, however it does not make sense to resolve the references...
     * (Moreover, there is no guarantee in which order the method of this class will be called, due to the undefined order of changes after the Diff calculation.)
     */
    override fun childrenChanged(nodeId: Long, role: String?) {
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MPS_WRITE),
            SyncDirection.MODELIX_TO_MPS,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            modelTransformer.resolveModelImports(project.repository)
            nodeTransformer.resolveReferences()

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
                nodeMovedToNewParent(newParentId, sNode, containmentLink, nodeId)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelMovedToNewParent(newParentId, nodeId, sModel)
                return@enqueue null
            }

            val isMapped = nodeMap.isMappedToMps(newParentId)
            // if isMapped == false, then we missed a possible new parent case
            logger.error("Node ($nodeId) was not moved to a new parent, because Modelix Node $newParentId was not mapped to MPS yet (isMapped=$isMapped).")

            null
        }
    }

    private fun getNode(nodeId: Long) = replicatedModel.getBranch().getNode(nodeId)

    private fun nodePropertyChanged(sNode: SNode, role: String, nodeId: Long, newValue: String?) {
        val sProperty = sNode.concept.properties.find { it.name == role }
        if (sProperty == null) {
            logger.error("Node ($nodeId)'s concept (${sNode.concept.name}) does not have property called $role.")
            return
        }

        val oldValue = sNode.getProperty(sProperty)
        if (oldValue != newValue) {
            sNode.setProperty(sProperty, newValue)
        }
    }

    private fun modelPropertyChanged(sModel: SModel, role: String, newValue: String?, nodeId: Long) {
        val modelId = sModel.modelId

        if (role == BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName()) {
            val oldValue = sModel.name.value
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    logger.error("Name cannot be null or empty for Model $modelId. Corresponding Modelix Node ID is $nodeId.")
                    return
                } else if (sModel !is EditableSModelBase) {
                    logger.error("SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Modelix Node ID is $nodeId.")
                    return
                }

                ModelRenameHelper(sModel).renameModel(newValue)
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype.getSimpleName()) {
            val oldValue = sModel.name.stereotype
            if (oldValue != newValue) {
                if (sModel !is EditableSModelBase) {
                    logger.error("SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Modelix Node ID is $nodeId.")
                    return
                }

                ModelRenameHelper(sModel).changeStereotype(newValue)
            }
        } else {
            logger.error("Role $role is unknown for concept Model. Therefore the property is not set in MPS from Modelix Node $nodeId")
        }
    }

    private fun modulePropertyChanged(role: String, nodeId: Long, sModule: SModule, newValue: String?) {
        val moduleId = sModule.moduleId
        if (sModule !is AbstractModule) {
            logger.error("SModule ($moduleId) is not an AbstractModule, therefore its $role property cannot be changed. Corresponding Modelix Node ID is $nodeId.")
            return
        }

        if (role == BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName()) {
            val oldValue = sModule.moduleName
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    logger.error("Name cannot be null or empty for Module $moduleId. Corresponding Modelix Node ID is $nodeId.")
                    return
                }

                val activeProject = ActiveMpsProjectInjector.activeMpsProject as Project
                Renamer(activeProject).renameModule(sModule, newValue)
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion.getSimpleName()) {
            try {
                val newVersion = newValue?.toInt() ?: return
                val oldVersion = sModule.moduleVersion
                if (oldVersion != newVersion) {
                    sModule.moduleVersion = newVersion
                }
            } catch (ex: NumberFormatException) {
                logger.error("New module version ($newValue) of SModule ($moduleId) is not an integer, therefore it cannot be set in MPS. Corresponding Modelix Node ID is $nodeId.")
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS.getSimpleName()) {
            try {
                val newCompileInMPS = newValue?.let { BooleanUtil.toBooleanStrict(it) } ?: return
                val moduleDescriptor = sModule.moduleDescriptor ?: return
                val oldCompileInMPS = moduleDescriptor.compileInMPS
                if (oldCompileInMPS != newCompileInMPS) {
                    if (moduleDescriptor !is SolutionDescriptor) {
                        logger.error("Module ($moduleId)'s descriptor is not a SolutionDescriptor, therefore compileInMPS will not be (un)set in MPS. Corresponding Modelix Node ID is $nodeId.")
                        return
                    }
                    moduleDescriptor.compileInMPS = newCompileInMPS
                }
            } catch (ex: ParseException) {
                logger.error("New compileInMPS ($newValue) property of SModule ($moduleId) is not a strict boolean, therefore it cannot be set in MPS. Corresponding Modelix Node ID is $nodeId.")
            }
        } else {
            logger.error("Role $role is unknown for concept Module. Therefore the property is not set in MPS from Modelix Node $nodeId")
        }
    }

    private fun nodeDeleted(it: SNode, nodeId: Long) {
        it.delete()
        nodeMap.remove(nodeId)
    }

    private fun modelDeleted(sModel: SModel, nodeId: Long) {
        ModelDeleteHelper(sModel).delete()
        nodeMap.remove(nodeId)
    }

    private fun moduleDeleted(sModule: SModule, nodeId: Long) {
        sModule.models.forEach { model ->
            val modelNodeId = nodeMap[model]
            ModelDeleteHelper(model).delete()
            modelNodeId?.let { nodeMap.remove(it) }
        }
        ModuleDeleteHelper(project).deleteModules(listOf(sModule), false, true)
        nodeMap.remove(nodeId)
    }

    private fun nodeMovedToNewParent(
        newParentId: Long,
        sNode: SNode,
        containmentLink: IChildLink,
        nodeId: Long,
    ) {
        // node moved to a new parent node
        val newParentNode = nodeMap.getNode(newParentId)
        newParentNode?.let {
            nodeMovedToNewParentNode(sNode, newParentNode, containmentLink, nodeId)
            return
        }

        // node moved to a new parent model
        val newParentModel = nodeMap.getModel(newParentId)
        newParentModel?.let {
            nodeMovedToNewParentModel(sNode, newParentModel)
            return
        }

        logger.error("Node ($nodeId) was neither moved to a new parent node nor to a new parent model, because Modelix Node $newParentId was not mapped to MPS yet.")
    }

    private fun nodeMovedToNewParentNode(sNode: SNode, newParent: SNode, containmentLink: IChildLink, nodeId: Long) {
        val oldParent = sNode.parent
        if (oldParent == newParent) {
            return
        }

        val containmentLinkName = containmentLink.getSimpleName()
        val containment = newParent.concept.containmentLinks.find { it.name == containmentLinkName }
        if (containment == null) {
            logger.error("Node ($nodeId)'s concept (${sNode.concept.name}) does not have containment link called $containmentLinkName.")
            return
        }

        // remove from old parent
        oldParent?.removeChild(sNode)
        sNode.model?.removeRootNode(sNode)

        // add to new parent
        newParent.addChild(containment, sNode)
    }

    private fun nodeMovedToNewParentModel(sNode: SNode, newParentModel: SModel) {
        val parentModel = sNode.model
        if (parentModel == newParentModel) {
            return
        }

        // remove from old parent
        parentModel?.removeRootNode(sNode)
        sNode.parent?.removeChild(sNode)

        // add to new parent
        newParentModel.addRootNode(sNode)
    }

    private fun modelMovedToNewParent(newParentId: Long, nodeId: Long, sModel: SModel) {
        val newParentModule = nodeMap.getModule(newParentId)
        if (newParentModule == null) {
            logger.error("Modelix Node ($nodeId) that is a Model, was not moved to a new parent module, because new parent Module (Modelix Node $newParentId) was not mapped to MPS yet.")
            return
        }

        val oldParentModule = sModel.module
        if (oldParentModule == newParentModule) {
            return
        }

        // remove from old parent
        if (oldParentModule !is SModuleBase) {
            logger.error("Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase. Therefore parent of Modelix Node $nodeId was not changed in MPS.")
            return
        } else if (sModel !is SModelBase) {
            logger.error("Model ${sModel.modelId} is not an SModelBase")
            return
        }
        oldParentModule.unregisterModel(sModel)
        sModel.module = null

        // add to new parent
        if (newParentModule !is SModuleBase) {
            logger.error("New parent Module ${newParentModule.moduleId} is not an SModuleBase. Therefore parent of Modelix Node $nodeId was not changed in MPS.")
            return
        }
        newParentModule.registerModel(sModel)
    }
}
