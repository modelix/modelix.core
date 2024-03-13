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

package org.modelix.mps.sync.transformation.mpsToModelix.initial

import com.jetbrains.rd.util.firstOrNull
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.Solution
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.api.getRootNode
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.bindTo
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleSynchronizer(private val branch: IBranch) {

    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val bindingsRegistry = BindingsRegistry

    private val modelSynchronizer = ModelSynchronizer(branch, postponeReferenceResolution = true)

    fun addModuleAndActivate(module: AbstractModule) {
        addModule(module, true).continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            @Suppress("UNCHECKED_CAST")
            (it as? Iterable<IBinding>)?.forEach(IBinding::activate)
        }
    }

    private fun addModule(
        module: AbstractModule,
        isTransformationStartingModule: Boolean = false,
    ): ContinuableSyncTask =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val rootNode = branch.getRootNode()
            val childLink = ChildLinkFromName("modules")
            val concept = BuiltinLanguages.MPSRepositoryConcepts.Module
            val cloudModule = rootNode.addNewChild(childLink, -1, concept)

            nodeMap.put(module, cloudModule.nodeIdAsLong())

            synchronizeModuleProperties(cloudModule, module)

            // synchronize dependencies
            module.declaredDependencies.waitForCompletionOfEachTask(collectResults = true) { addDependency(module, it) }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { unflattenedBindings ->
            @Suppress("UNCHECKED_CAST")
            (unflattenedBindings as Iterable<Iterable<IBinding>>).flatten()
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) { dependencyBindings ->
            // synchronize models
            val modelSynchedFuture =
                module.models.waitForCompletionOfEachTask { modelSynchronizer.addModel(it as SModelBase) }

            // pass on the dependencyBindings after the modelSynchedFuture is completed
            val passedOnDependencyBindingsFuture = CompletableFuture<Any?>()
            modelSynchedFuture.whenComplete { _, throwable ->
                if (throwable != null) {
                    passedOnDependencyBindingsFuture.completeExceptionally(throwable)
                } else {
                    passedOnDependencyBindingsFuture.complete(dependencyBindings)
                }
            }
            passedOnDependencyBindingsFuture
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
        ) { dependencyBindings ->
            // resolve references only after all dependent (and contained) modules and models have been transformed
            if (isTransformationStartingModule) {
                resolveCrossModelReferences()
            }
            dependencyBindings
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.MPS_TO_MODELIX) { dependencyBindings ->
            // register binding
            val binding = ModuleBinding(module, branch)
            bindingsRegistry.addModuleBinding(binding)

            val bindings = mutableSetOf<IBinding>(binding)
            @Suppress("UNCHECKED_CAST")
            bindings.addAll(dependencyBindings as Iterable<IBinding>)
            bindings
        }

    fun addDependency(module: SModule, dependency: SDependency) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val repository = ActiveMpsProjectInjector.activeMpsProject?.repository!!
            val targetModule = dependency.targetModule.resolve(repository)
            val isMappedToMps = nodeMap[targetModule] != null

            val future = CompletableFuture<Any?>()
            if (!isMappedToMps) {
                require(targetModule is AbstractModule) { "Dependency target module ($targetModule) of Module ($module) must be an AbstractModule." }
                // connect the addModule task to this one, so if that fails/succeeds we'll also fail/succeed
                addModule(targetModule).getResult().bindTo(future)
            } else {
                future.complete(setOf(EmptyBinding()))
            }
            future
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
        ) { dependencyBindings ->
            val moduleModelixId = nodeMap[module]!!
            val dependencies = BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies

            val cloudModule = branch.getNode(moduleModelixId)
            val cloudDependency =
                cloudModule.addNewChild(dependencies, -1, BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency)

            val moduleReference = dependency.targetModule
            nodeMap.put(module, moduleReference, cloudDependency.nodeIdAsLong())

            // warning: might be fragile, because we synchronize the properties by hand
            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport,
                dependency.isReexport.toString(),
            )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid,
                moduleReference.moduleId.toString(),
            )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name,
                moduleReference.moduleName,
            )

            val moduleId = moduleReference.moduleId
            val isExplicit = if (module is Solution) {
                module.moduleDescriptor.dependencies.any { it.moduleRef.moduleId == moduleId }
            } else {
                module.declaredDependencies.any { it.targetModule.moduleId == moduleId }
            }
            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.explicit,
                isExplicit.toString(),
            )

            val version = (module as? Solution)?.let {
                it.moduleDescriptor.dependencyVersions.filter { dependencyVersion -> dependencyVersion.key == moduleReference }
                    .firstOrNull()?.value
            } ?: 0
            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.version,
                version.toString(),
            )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.scope,
                dependency.scope.toString(),
            )

            dependencyBindings
        }

    private fun synchronizeModuleProperties(cloudModule: INode, module: SModule) {
        cloudModule.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Module.id,
            module.moduleId.toString(),
        )
        cloudModule.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion,
            ((module as? AbstractModule)?.moduleVersion).toString(),
        )

        val compileInMPS =
            module is AbstractModule && module !is DevKit && module.moduleDescriptor?.compileInMPS == true
        cloudModule.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS,
            compileInMPS.toString(),
        )

        cloudModule.setPropertyValue(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
            module.moduleName,
        )
    }

    fun resolveCrossModelReferences() = modelSynchronizer.resolveCrossModelReferences()
}
