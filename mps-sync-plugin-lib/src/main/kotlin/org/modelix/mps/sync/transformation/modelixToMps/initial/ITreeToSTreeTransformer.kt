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
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.SModelInternal
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModelChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModuleChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.NodeChangeListener
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ITreeToSTreeTransformer(
    private val replicatedModel: ReplicatedModel,
    private val project: MPSProject,
    mpsLanguageRepository: MPSLanguageRepository,
    private val isSynchronizing: AtomicReference<Boolean>,
    private val nodeMap: MpsToModelixMap,
) {

    private val logger = logger<ITreeToSTreeTransformer>()

    private val nodeTransformer = NodeTransformer(project.modelAccess, nodeMap, mpsLanguageRepository)
    private val modelTransformer = ModelTransformer(project.modelAccess, nodeMap)
    private val moduleTransformer = ModuleTransformer(project, nodeMap)

    fun transform(entryPoint: INode): SNode? {
        try {
            isSynchronizing.set(true)
            // TODO use coroutines instead of big-bang eager loading?
            val branch = replicatedModel.getBranch()
            branch.runReadT {
                val root = branch.getNode(entryPoint.nodeIdAsLong())

                logger.info("--- PRINTING TREE ---")
                traverse(root, 1) { }

                logger.info("--- FILTERING MODULES AND MODELS ---")
                traverse(root, 1) {
                    if (it.isModule()) {
                        moduleTransformer.transformToModule(it)
                    } else if (it.isModel()) {
                        modelTransformer.transformToModel(it)
                    }
                }

                logger.info("--- TRANSFORMING NODES ---")
                traverse(root, 1) {
                    val isNotModuleOrModel = !(it.isModule() || it.isModel())
                    if (isNotModuleOrModel) {
                        nodeTransformer.transformToNode(it)
                    }
                }

                logger.info("--- RESOLVING REFERENCES AND MODEL IMPORTS ---")
                nodeTransformer.resolveReferences()
                modelTransformer.resolveModelImports(project.repository)

                logger.info("--- REGISTER LISTENERS, AKA \"ACTIVATE BINDINGS\"")
                nodeMap.models.forEach {
                    val nodeChangeListener = NodeChangeListener(branch, nodeMap, isSynchronizing)
                    it.addChangeListener(nodeChangeListener)

                    val modelChangeListener = ModelChangeListener(branch, nodeMap, isSynchronizing)
                    (it as SModelInternal).addModelListener(modelChangeListener)
                }
                nodeMap.modules.forEach {
                    it.addModuleListener(ModuleChangeListener(branch, nodeMap, isSynchronizing))
                }
            }
        } catch (ex: Exception) {
            logger.error("$javaClass exploded")
            ex.printStackTrace()
        } finally {
            isSynchronizing.set(false)
        }

        return null
    }

    private fun traverse(parent: INode, level: Int, processNode: (INode) -> Unit) {
        processNode(parent)
        parent.allChildren.forEach {
            traverse(it, level + 1, processNode)
        }
    }
}
