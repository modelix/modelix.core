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
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.runIfAlone
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new mod elix MPS plugin is under construction", intendedFinalization = "2024.1")
class TreeChangeVisitor(
    private val replicatedModel: ReplicatedModel,
    project: MPSProject,
    private val languageRepository: MPSLanguageRepository,
    private val isSynchronizing: AtomicReference<Boolean>,
    private val nodeMap: MpsToModelixMap,
) : ITreeChangeVisitorEx {

    private val logger = logger<TreeChangeVisitor>()

    private val modelAccess = project.modelAccess

    private val nodeTransformer = NodeTransformer(project, nodeMap, languageRepository)
    private val modelTransformer = ModelTransformer(project, nodeMap)
    private val moduleTransformer = ModuleTransformer(project, nodeMap)

    override fun referenceChanged(nodeId: Long, role: String) {
        // TODO how to transform modelImport, modelDependency, languageDependency changes?
        // TODO Are they also "referenceChanged" events?
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)!!
            var sReferenceLink: SReferenceLink? = null
            modelAccess.runReadBlocking {
                sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }
            }

            val iNode = getBranch().getNode(nodeId)
            var iReferenceLink: IReferenceLink?
            var targetINode: INode? = null
            getBranch().runRead {
                iReferenceLink = iNode.getReferenceLinks().find { it.getSimpleName() == role }
                targetINode = iReferenceLink?.let { iNode.getReferenceTarget(it) }
            }
            val targetSNode = targetINode?.let { nodeMap.getNode(it.nodeIdAsLong()) }

            modelAccess.runWriteActionInEDTBlocking {
                sNode.setReferenceTarget(sReferenceLink!!, targetSNode)
            }
        }
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)!!
            var sProperty: SProperty? = null
            modelAccess.runReadBlocking {
                sProperty = sNode.concept.properties.find { it.name == role }
            }

            val iNode = getBranch().getNode(nodeId)
            val iProperty = PropertyFromName(role)
            var value: String? = null
            getBranch().runRead { value = iNode.getPropertyValue(iProperty) }

            modelAccess.runWriteActionInEDTBlocking {
                sNode.setProperty(sProperty!!, value)
            }
        }
    }

    override fun nodeRemoved(nodeId: Long) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                modelAccess.runWriteActionInEDTBlocking {
                    it.delete()
                    nodeMap.remove(nodeId)
                }
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                val sModule = sModel.module
                require(sModel is SModelBase) { "Model ${sModel.modelId} is not SModelBase" }
                require(sModule is SModuleBase) { "Module ${sModule.moduleId} is not SModuleBase" }
                sModule.unregisterModel(sModel)
                nodeMap.remove(nodeId)
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                require(sModule is SModuleBase) { "Module ${sModule.moduleId} is not SModuleBase" }
                sModule.dispose()
                nodeMap.remove(nodeId)
            }
        }
    }

    override fun nodeAdded(nodeId: Long) {
        isSynchronizing.runIfAlone(::handleThrowable) {
            val iNode = getBranch().getNode(nodeId)

            getBranch().runRead {
                if (iNode.isModule()) {
                    moduleTransformer.transformToModule(iNode)
                } else if (iNode.isModel()) {
                    modelTransformer.transformToModel(iNode)
                } else {
                    nodeTransformer.transformToNode(iNode)
                }
            }
        }
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        modelTransformer.resolveModelImports(languageRepository.repository)
        modelTransformer.clearResolvableModelImports()

        nodeTransformer.resolveReferences()
        nodeTransformer.clearResolvableReferences()
    }

    override fun containmentChanged(nodeId: Long) {
        logger.info("TODO IMPLEMENT ME!!! Containment changed for Node $nodeId")
    }

    private fun handleThrowable(t: Throwable) = t.printStackTrace()

    private fun getBranch() = replicatedModel.getBranch()
}
