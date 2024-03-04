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

import jetbrains.mps.extapi.model.SModelBase
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.getNode
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.mps.ApplicationLifecycleTracker
import org.modelix.mps.sync.tasks.InspectionMode
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer
import org.modelix.mps.sync.util.bindTo
import org.modelix.mps.sync.util.completeWithDefault
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleChangeListener(
    private val branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val bindingsRegistry: BindingsRegistry,
    private val syncQueue: SyncQueue,
) : SModuleListener {

    private val moduleSynchronizer = ModuleSynchronizer(branch, nodeMap, bindingsRegistry, syncQueue)
    private val modelSynchronizer = ModelSynchronizer(branch, nodeMap, bindingsRegistry, syncQueue)
    private val nodeSynchronizer = NodeSynchronizer(branch, nodeMap, syncQueue)

    private val moduleChangeSyncInProgress = synchronizedLinkedHashSet<SModule>()

    override fun modelAdded(module: SModule, model: SModel) = modelSynchronizer.addModelAndActivate(model as SModelBase)

    override fun modelRemoved(module: SModule, reference: SModelReference) {
        if (ApplicationLifecycleTracker.applicationClosing) {
            return
        }

        val modelId = reference.modelId
        val binding = bindingsRegistry.getModelBinding(modelId)
        // if binding is not found, it means the model should be removed (see ModelBinding's deactivate method)
        if (binding == null) {
            nodeSynchronizer.removeNode(
                parentNodeIdProducer = { it[module]!! },
                childNodeIdProducer = { it[modelId]!! },
            )
        }
    }

    override fun moduleChanged(module: SModule) {
        synchronized(module) {
            /**
             * in some cases MPS might call this method multiple times consecutively(e.g. when we add the new
             * dependency), and we want to avoid breaking an ongoing synchronizations.
             */
            if (moduleChangeSyncInProgress.contains(module)) {
                return
            }
            moduleChangeSyncInProgress.add(module)
        }

        syncQueue.enqueue(
            linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            try {
                // check if name is the same
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
                val iName = iModule.getPropertyValue(nameProperty)
                val actualName = module.moduleName!!

                val future = CompletableFuture<Any?>()
                if (actualName != iName) {
                    nodeSynchronizer.setProperty(
                        nameProperty,
                        actualName,
                        sourceNodeIdProducer = { iModuleNodeId },
                    ).getResult().bindTo(future)
                } else {
                    future.completeWithDefault()
                }
                future.exceptionally { removeModuleFromSyncInProgressAndRethrow(module, it) }
            } catch (t: Throwable) {
                removeModuleFromSyncInProgressAndRethrow(module, t)
            }
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            try {
                // add new dependencies
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val lastKnownDependencies =
                    iModule.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                val actualDependencies = module.declaredDependencies

                val addedDependencies = actualDependencies.filter { sDependency ->
                    lastKnownDependencies.none { dependencyINode ->
                        val targetModuleId = sDependency.targetModule.moduleId
                        ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode) == targetModuleId
                    }
                }
                addedDependencies.waitForCompletionOfEachTask { dependency ->
                    moduleSynchronizer.addDependency(
                        module,
                        dependency,
                    )
                }.exceptionally { removeModuleFromSyncInProgressAndRethrow(module, it) }
            } catch (t: Throwable) {
                removeModuleFromSyncInProgressAndRethrow(module, t)
            }
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ),
            SyncDirection.MPS_TO_MODELIX,
            InspectionMode.CHECK_EXECUTION_THREAD,
        ) {
            try {
                // remove deleted dependencies
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val lastKnownDependencies =
                    iModule.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                val actualDependencies = module.declaredDependencies

                val removedDependencies = lastKnownDependencies.filter { dependencyINode ->
                    val targetModuleIdAccordingToModelix =
                        ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode)
                    actualDependencies.none { sDependency ->
                        targetModuleIdAccordingToModelix == sDependency.targetModule.moduleId
                    }
                }
                removedDependencies.waitForCompletionOfEachTask { dependencyINode ->
                    nodeSynchronizer.removeNode(
                        parentNodeIdProducer = { it[module]!! },
                        childNodeIdProducer = { dependencyINode.nodeIdAsLong() },
                    )
                }.handle { result, throwable ->
                    removeModuleFromSyncInProgressAndRethrow(module, throwable)
                    result
                }
            } catch (t: Throwable) {
                removeModuleFromSyncInProgressAndRethrow(module, t)
            }
        }
    }

    private fun removeModuleFromSyncInProgressAndRethrow(module: SModule, throwable: Throwable?) {
        moduleChangeSyncInProgress.remove(module)
        throwable?.let { throw it }
    }

    override fun dependencyAdded(module: SModule, dependency: SDependency) {
        // handled by moduleChanged, because this method is never called
    }

    override fun dependencyRemoved(module: SModule, dependency: SDependency) {
        // handled by moduleChanged, because this method is never called
    }

    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {
        // duplicate of SModelListener.modelRenamed
    }

    override fun languageAdded(module: SModule, language: SLanguage) {}
    override fun languageRemoved(module: SModule, language: SLanguage) {}
    override fun beforeModelRemoved(module: SModule, model: SModel) {}
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}
}
