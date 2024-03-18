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
import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.api.getRootNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.factories.SolutionProducer
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.ModuleWithModuleReference
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.BooleanUtil
import org.modelix.mps.sync.util.bindTo
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.text.ParseException
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleTransformer(private val branch: IBranch, mpsLanguageRepository: MPSLanguageRepository) {

    companion object {
        fun getTargetModuleIdFromModuleDependency(moduleDependency: INode): SModuleId {
            val uuid = moduleDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid)!!
            return PersistenceFacade.getInstance().createModuleId(uuid)
        }
    }

    private val logger = KotlinLogging.logger {}
    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val bindingsRegistry = BindingsRegistry
    private val project
        get() = ActiveMpsProjectInjector.activeMpsProject!!

    private val solutionProducer = SolutionProducer()

    private val modelTransformer = ModelTransformer(branch, mpsLanguageRepository)

    fun transformToModuleCompletely(nodeId: Long, isTransformationStartingModule: Boolean = false) =
        transformToModule(nodeId, true)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) { dependencyBindings ->
                // transform models
                val module = branch.getNode(nodeId)
                val modelBindingsFuture = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                    .waitForCompletionOfEachTask(collectResults = true) {
                        modelTransformer.transformToModelCompletely(it.nodeIdAsLong(), branch, bindingsRegistry)
                    }

                // join the newly added model bindings with the existing bindings
                val collectedBindingsFuture = CompletableFuture<Any?>()
                FuturesWaitQueue.add(
                    collectedBindingsFuture,
                    setOf(modelBindingsFuture, CompletableFuture.completedFuture(dependencyBindings)),
                    collectResults = true,
                )
                collectedBindingsFuture
            }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { unflattenedBindings ->
                @Suppress("UNCHECKED_CAST")
                (unflattenedBindings as Iterable<Iterable<IBinding>>).flatten()
            }.continueWith(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) { dependencyAndModelBindings ->
                // resolve references only after all dependent (and contained) modules and models have been transformed
                if (isTransformationStartingModule) {
                    // resolve cross-model references (and node references)
                    modelTransformer.resolveCrossModelReferences(project.repository)
                }
                dependencyAndModelBindings
            }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.MODELIX_TO_MPS) { dependencyAndModelBindings ->
                // register binding
                val iNode = branch.getNode(nodeId)
                val module = nodeMap.getModule(iNode.nodeIdAsLong()) as AbstractModule
                val moduleBinding = ModuleBinding(module, branch)
                bindingsRegistry.addModuleBinding(moduleBinding)

                val bindings = mutableSetOf<IBinding>()
                @Suppress("UNCHECKED_CAST")
                bindings.addAll(dependencyAndModelBindings as Iterable<IBinding>)
                bindings.add(moduleBinding)
                bindings
            }

    fun transformToModule(nodeId: Long, fetchTargetModule: Boolean = false) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
            check(serializedId.isNotEmpty()) { "Module's ($iNode) ID is empty" }

            val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
            val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            check(name != null) { "Module's ($iNode) name is null" }

            val sModule = solutionProducer.createOrGetModule(name, moduleId as ModuleId)
            nodeMap.put(sModule, iNode.nodeIdAsLong())

            // transform dependencies and collect its bindings (implicitly via the tasks' return value)
            iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                .waitForCompletionOfEachTask(collectResults = true) {
                    transformModuleDependency(it.nodeIdAsLong(), sModule, fetchTargetModule)
                }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { unflattenedBindings ->
            @Suppress("UNCHECKED_CAST")
            (unflattenedBindings as Iterable<Iterable<IBinding>>).flatten()
        }

    fun transformModuleDependency(
        nodeId: Long,
        parentModule: AbstractModule,
        fetchTargetModule: Boolean = false,
    ): ContinuableSyncTask =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val targetModuleId = getTargetModuleIdFromModuleDependency(iNode)

            val future = CompletableFuture<Any?>()
            val targetModuleIsNotMapped = nodeMap.getModule(targetModuleId) == null
            if (targetModuleIsNotMapped && fetchTargetModule) {
                // find target module in modelix
                val targetModule = branch.getRootNode().getChildren(ChildLinkFromName("modules")).firstOrNull {
                    val serializedId = it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id)
                    serializedId?.let {
                        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
                        moduleId == targetModuleId
                    } ?: false
                }

                if (targetModule != null) {
                    transformToModuleCompletely(targetModule.nodeIdAsLong()).getResult().bindTo(future)
                } else {
                    /*
                     * Issue MODELIX-820:
                     * A manual quick-fix would be if the module is available on another model server and its ID is
                     * the same as what is used in the ModuleDependency, then just upload that module to this server
                     * and rerun the transformation.
                     * TODO (1) Show it as a suggestion to the user?
                     * TODO (2) As a final fallback, the user could remove the module dependency. In this case, we have
                     * to implement the corresponding feature (e.g., as an action by clicking on a button). Maybe this
                     * direction would be easier for the user and for us too.
                     */
                    val targetModuleName =
                        iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                    val ex =
                        NoSuchElementException("Target Module ($targetModuleName) of ModuleDependency ($nodeId) that goes out from Module ${parentModule.moduleName} is not found on the server.")
                    logger.error(ex) { ex.message }
                    future.completeExceptionally(ex)
                }
            } else {
                future.complete(setOf(EmptyBinding()))
            }

            future
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE),
            SyncDirection.MODELIX_TO_MPS,
        ) { dependencyBinding ->
            val iNode = branch.getNode(nodeId)
            val targetModuleId = getTargetModuleIdFromModuleDependency(iNode)

            val moduleName = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name)
            val moduleReference = ModuleReference(moduleName, targetModuleId)
            val reexport = (
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport)
                    ?: "false"
                ).toBoolean()
            parentModule.addDependency(moduleReference, reexport)

            nodeMap.put(parentModule, moduleReference, iNode.nodeIdAsLong())

            dependencyBinding
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
