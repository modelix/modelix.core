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

package org.modelix.mps.sync.transformation.modelixToMps.transformers

import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Project
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.refactoring.Renamer
import mu.KotlinLogging
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.factories.SolutionProducer
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.ModuleWithModuleReference
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.BooleanUtil
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.text.ParseException

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleTransformer(
    private val nodeMap: MpsToModelixMap,
    private val syncQueue: SyncQueue,
    private val project: MPSProject,
    mpsLanguageRepository: MPSLanguageRepository,
) {

    companion object {
        fun getTargetModuleIdFromModuleDependency(moduleDependency: INode): SModuleId {
            val uuid = moduleDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid)!!
            return PersistenceFacade.getInstance().createModuleId(uuid)
        }
    }

    private val logger = KotlinLogging.logger {}

    private val solutionProducer = SolutionProducer(project)

    private val modelTransformer = ModelTransformer(nodeMap, syncQueue, mpsLanguageRepository)

    fun transformToModuleCompletely(iNode: INode, branch: IBranch, bindingsRegistry: BindingsRegistry) =
        transformToModule(iNode)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
                iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).waitForCompletionOfEachTask {
                    // transform models
                    modelTransformer.transformToModelCompletely(it, branch, bindingsRegistry)
                }
            }.continueWith(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                // resolve cross-model references (and node references)
                modelTransformer.resolveCrossModelReferences(project.repository)
            }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.MODELIX_TO_MPS) {
                // register binding
                val module = nodeMap.getModule(iNode.nodeIdAsLong()) as AbstractModule
                val moduleBinding = ModuleBinding(module, branch, nodeMap, bindingsRegistry, syncQueue)
                bindingsRegistry.addModuleBinding(moduleBinding)

                val modelBindings = bindingsRegistry.getModelBindings(module)!!
                val bindings = mutableSetOf<IBinding>(moduleBinding)
                bindings.addAll(modelBindings)

                bindings
            }

    fun transformToModule(iNode: INode) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
            check(serializedId.isNotEmpty()) { "Module's ($iNode) ID is empty" }

            val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
            val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            check(name != null) { "Module's ($iNode) name is null" }

            val sModule = solutionProducer.createOrGetModule(name, moduleId as ModuleId)
            nodeMap.put(sModule, iNode.nodeIdAsLong())

            // transform dependencies
            iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).waitForCompletionOfEachTask {
                transformModuleDependency(it, sModule)
            }
        }

    fun transformModuleDependency(iNode: INode, parentModule: AbstractModule) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val reexport = (
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport)
                    ?: "false"
                ).toBoolean()

            // TODO if target module is not synchronized yet then try to find it and download
            // if not found then throw exception

            val moduleName = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name)
            val moduleId = getTargetModuleIdFromModuleDependency(iNode)
            val moduleReference = ModuleReference(moduleName, moduleId)
            parentModule.addDependency(moduleReference, reexport)

            nodeMap.put(parentModule, moduleReference, iNode.nodeIdAsLong())
        }

    fun modulePropertyChanged(role: String, nodeId: Long, sModule: SModule, newValue: String?) {
        val moduleId = sModule.moduleId
        if (sModule !is AbstractModule) {
            logger.error { "SModule ($moduleId) is not an AbstractModule, therefore its $role property cannot be changed. Corresponding Modelix Node ID is $nodeId." }
            return
        }

        if (role == BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName()) {
            val oldValue = sModule.moduleName
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    logger.error { "Name cannot be null or empty for Module $moduleId. Corresponding Modelix Node ID is $nodeId." }
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
                logger.error { "New module version ($newValue) of SModule ($moduleId) is not an integer, therefore it cannot be set in MPS. Corresponding Modelix Node ID is $nodeId." }
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS.getSimpleName()) {
            try {
                val newCompileInMPS = newValue?.let { BooleanUtil.toBooleanStrict(it) } ?: return
                val moduleDescriptor = sModule.moduleDescriptor ?: return
                val oldCompileInMPS = moduleDescriptor.compileInMPS
                if (oldCompileInMPS != newCompileInMPS) {
                    if (moduleDescriptor !is SolutionDescriptor) {
                        logger.error { "Module ($moduleId)'s descriptor is not a SolutionDescriptor, therefore compileInMPS will not be (un)set in MPS. Corresponding Modelix Node ID is $nodeId." }
                        return
                    }
                    moduleDescriptor.compileInMPS = newCompileInMPS
                }
            } catch (ex: ParseException) {
                logger.error { "New compileInMPS ($newValue) property of SModule ($moduleId) is not a strict boolean, therefore it cannot be set in MPS. Corresponding Modelix Node ID is $nodeId." }
            }
        } else {
            logger.error { "Role $role is unknown for concept Module. Therefore the property is not set in MPS from Modelix Node $nodeId" }
        }
    }

    fun moduleDeleted(sModule: SModule, nodeId: Long) {
        sModule.models.forEach { model ->
            val modelNodeId = nodeMap[model]
            ModelDeleteHelper(model).delete()
            modelNodeId?.let { nodeMap.remove(it) }
        }
        val project = ActiveMpsProjectInjector.activeMpsProject!!
        ModuleDeleteHelper(project).deleteModules(listOf(sModule), false, true)
        nodeMap.remove(nodeId)
    }

    fun outgoingModuleReferenceFromModuleDeleted(moduleWithModuleReference: ModuleWithModuleReference, nodeId: Long) {
        val sourceModule = moduleWithModuleReference.source
        if (sourceModule !is AbstractModule) {
            logger.error { "Source module ($sourceModule) is not an AbstractModule, therefore outgoing module dependency reference cannot be removed. Corresponding Modelix Node ID is $nodeId." }
            return
        }

        val targetModuleReference = moduleWithModuleReference.moduleReference
        val dependency =
            sourceModule.moduleDescriptor?.dependencies?.firstOrNull { it.moduleRef == targetModuleReference }
        if (dependency != null) {
            sourceModule.removeDependency(dependency)
        } else {
            logger.error { "Outgoing dependency $targetModuleReference from Module $sourceModule is not found, therefore it cannot be deleted. Corresponding Modelix Node ID is $nodeId." }
        }
    }
}
