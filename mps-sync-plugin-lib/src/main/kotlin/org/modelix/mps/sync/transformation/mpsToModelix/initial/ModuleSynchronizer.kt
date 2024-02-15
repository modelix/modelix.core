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
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.tasks.InspectionMode
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleSynchronizer(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val bindingsRegistry: BindingsRegistry,
    private val syncQueue: SyncQueue,
) {

    private val modelSynchronizer =
        ModelSynchronizer(branch, nodeMap, bindingsRegistry, syncQueue, postponeReferenceResolution = true)

    fun addModule(module: AbstractModule) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val rootNode = branch.getRootNode()
            val childLink = ChildLinkFromName("modules")
            val concept = BuiltinLanguages.MPSRepositoryConcepts.Module
            val cloudModule = rootNode.addNewChild(childLink, -1, concept)

            nodeMap.put(module, cloudModule.nodeIdAsLong())

            synchronizeModuleProperties(cloudModule, module)
            // synchronize dependencies
            module.declaredDependencies.waitForCompletionOfEachTask { addDependency(module, it) }
        }.continueWith(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // synchronize models
            module.models.waitForCompletionOfEachTask { modelSynchronizer.addModel(it as SModelBase) }
        }.continueWith(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // resolve cross-model references
            modelSynchronizer.resolveCrossModelReferences()
        }.continueWith(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // register binding
            val binding = ModuleBinding(module, branch, nodeMap, bindingsRegistry, syncQueue)
            bindingsRegistry.addModuleBinding(binding)
            binding.activate()
        }
    }

    fun addDependency(module: SModule, dependency: SDependency) =
        syncQueue.enqueue(
            linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            val moduleModelixId = nodeMap[module]!!
            val dependencies = BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies
            val moduleReference = dependency.targetModule

            val cloudModule = branch.getNode(moduleModelixId)
            val cloudDependency =
                cloudModule.addNewChild(dependencies, -1, BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency)

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
        }

    private fun synchronizeModuleProperties(cloudModule: INode, module: SModule) {
        cloudModule.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Module.id,
            module.moduleId.toString(),
        )
        cloudModule.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion,
            ((module as? AbstractModule)?.moduleDescriptor?.moduleVersion ?: 0).toString(),
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
}
