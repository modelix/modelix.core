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

package org.modelix.mps.sync.transformation.modelixToMps.initial

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ITreeToSTreeTransformer(
    private val branch: IBranch,
    private val project: MPSProject,
    mpsLanguageRepository: MPSLanguageRepository,
    private val nodeMap: MpsToModelixMap,
    private val bindingsRegistry: BindingsRegistry,
    private val syncQueue: SyncQueue,
) {

    private val logger = logger<ITreeToSTreeTransformer>()

    private val nodeTransformer = NodeTransformer(nodeMap, syncQueue, mpsLanguageRepository)
    private val modelTransformer = ModelTransformer(nodeMap, syncQueue)
    private val moduleTransformer = ModuleTransformer(nodeMap, syncQueue, project)

    fun transform(entryPoint: INode): List<IBinding> {
        val bindings = mutableListOf<IBinding>()

        try {
            syncQueue.enqueueBlocking(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
                val nodeId = entryPoint.nodeIdAsLong()
                val root = branch.getNode(nodeId)

                logger.info("--- Transforming modules and models in modelix Node $nodeId ---")
                traverse(root, 1) {
                    if (it.isModule()) {
                        moduleTransformer.transformToModule(it)
                    } else if (it.isModel()) {
                        modelTransformer.transformToModel(it)
                    }
                }

                logger.info("--- Resolving model imports ---")
                modelTransformer.resolveModelImports(project.repository)

                logger.info("--- Transforming nodes ---")
                traverse(root, 1) {
                    val isNotModuleOrModel = !(it.isModule() || it.isModel())
                    if (isNotModuleOrModel) {
                        nodeTransformer.transformToNode(it)
                    }
                }

                logger.info("--- Resolving references ---")
                nodeTransformer.resolveReferences()

                logger.info("--- Registering module and model bindings ---")
                nodeMap.modules.forEach {
                    val module = it as AbstractModule
                    val binding = ModuleBinding(module, branch, nodeMap, bindingsRegistry, syncQueue)
                    bindingsRegistry.addModuleBinding(binding)
                    bindings.add(binding)
                }
                nodeMap.models.forEach {
                    val model = it as SModelBase
                    val binding = ModelBinding(model, branch, nodeMap, bindingsRegistry, syncQueue)
                    bindingsRegistry.addModelBinding(binding)
                    bindings.add(binding)
                }
            }
        } catch (ex: Exception) {
            logger.error("Transformation of Node tree starting from Node $entryPoint failed.", ex)
        }

        return bindings
    }

    private fun traverse(parent: INode, level: Int, processNode: (INode) -> Unit) {
        processNode(parent)
        parent.allChildren.forEach {
            traverse(it, level + 1, processNode)
        }
    }
}