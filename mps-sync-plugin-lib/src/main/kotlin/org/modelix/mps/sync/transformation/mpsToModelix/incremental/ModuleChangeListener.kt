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
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer
import org.modelix.mps.sync.util.SyncLock
import org.modelix.mps.sync.util.SyncQueue
import org.modelix.mps.sync.util.nodeIdAsLong

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
        // calculate the difference in dependencies between the SModule's INode and what is in the SModule.declaredDependencies
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), true) {
            val actualDependencies = module.declaredDependencies

            val iModuleNodeId = nodeMap[module]!!
            val iModule = branch.getNode(iModuleNodeId)
            val lastKnownDependencies = iModule.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)

            val addedDependencies = actualDependencies.filter { sDependency ->
                lastKnownDependencies.none { dependencyINode ->
                    ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode) == sDependency.targetModule.moduleId
                }
            }
            addedDependencies.forEach { dependency -> moduleSynchronizer.runAddDependencyAction(module, dependency) }

            val removedDependencies = lastKnownDependencies.filter { dependencyINode ->
                val targetModuleIdAccordingToModelix =
                    ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode)
                actualDependencies.none { sDependency ->
                    targetModuleIdAccordingToModelix == sDependency.targetModule.moduleId
                }
            }
            removedDependencies.forEach { dependencyINode ->
                nodeSynchronizer.runRemoveNodeAction(
                    parentNodeIdProducer = { it[module]!! },
                    childNodeIdProducer = { dependencyINode.nodeIdAsLong() },
                )
            }
        }
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
