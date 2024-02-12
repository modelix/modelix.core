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

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.factories.SNodeFactory
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.addLanguageImport
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeTransformer(
    private val nodeMap: MpsToModelixMap,
    private val syncQueue: SyncQueue,
    mpsLanguageRepository: MPSLanguageRepository,
) {

    private val logger = logger<NodeTransformer>()
    private val nodeFactory = SNodeFactory(mpsLanguageRepository, nodeMap, syncQueue)

    fun transformToNode(iNode: INode) {
        if (iNode.isDevKitDependency()) {
            transformDevKitDependency(iNode)
        } else if (iNode.isSingleLanguageDependency()) {
            transformLanguageDependency(iNode)
        } else {
            try {
                val modelId = iNode.getModel()?.nodeIdAsLong()
                val model = nodeMap.getModel(modelId)
                val isTransformed = nodeMap.isMappedToMps(iNode.nodeIdAsLong())
                if (!isTransformed) {
                    if (model == null) {
                        logger.info("Node ${iNode.nodeIdAsLong()}(${iNode.concept?.getLongName() ?: "concept null"}) was not transformed, because model is null.")
                    } else {
                        nodeFactory.createNode(iNode, model)
                    }
                }
            } catch (ex: Exception) {
                logger.error("Transformation of Node ($iNode) failed.", ex)
            }
        }
    }

    fun transformLanguageDependency(iNode: INode) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val dependentModule = getDependentModule(iNode)
            val languageModuleReference = (dependentModule as Language).moduleReference
            val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference)
            val languageVersion =
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)!!
                    .toInt()

            val parent = iNode.parent!!
            val parentNodeId = parent.nodeIdAsLong()
            val parentIsModule = parent.isModule()
            val parentIsModel = parent.isModel()

            if (parentIsModule) {
                val parentModule = nodeMap.getModule(parentNodeId)!!
                parentModule.models.forEach {
                    it.addLanguageImport(sLanguage, languageVersion)
                    nodeMap.put(it, languageModuleReference, iNode.nodeIdAsLong())
                }
            } else if (parentIsModel) {
                val parentModel = nodeMap.getModel(parentNodeId)!!
                parentModel.addLanguageImport(sLanguage, languageVersion)
                nodeMap.put(parentModel, languageModuleReference, iNode.nodeIdAsLong())
            } else {
                logger.error("Node ${iNode.nodeIdAsLong()}'s parent is neither a Module nor a Model, thus the Language Dependency is not added to the model/module.")
            }
        }
    }

    fun transformDevKitDependency(iNode: INode) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val dependentModule = getDependentModule(iNode)
            val devKitModuleReference = (dependentModule as DevKit).moduleReference

            val parent = iNode.parent!!
            val parentNodeId = parent.nodeIdAsLong()
            val parentIsModule = parent.isModule()
            val parentIsModel = parent.isModel()

            if (parentIsModule) {
                val parentModule = nodeMap.getModule(parentNodeId)!!
                parentModule.models.forEach {
                    it.addDevKit(devKitModuleReference)
                    nodeMap.put(it, devKitModuleReference, iNode.nodeIdAsLong())
                }
            } else if (parentIsModel) {
                val parentModel = nodeMap.getModel(parentNodeId)!!
                parentModel.addDevKit(devKitModuleReference)
                nodeMap.put(parentModel, devKitModuleReference, iNode.nodeIdAsLong())
            } else {
                logger.error("Node ${iNode.nodeIdAsLong()}'s parent is neither a Module nor a Model, thus DevKit is not added to the model/module.")
            }
        }
    }

    fun resolveReferences() = nodeFactory.resolveReferences()

    fun clearResolvableReferences() = nodeFactory.clearResolvableReferences()

    private fun getDependentModule(iNode: INode): SModule {
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)!!
        val activeProject = ActiveMpsProjectInjector.activeMpsProject!!
        return activeProject.repository.getModule(ModuleId.regular(UUID.fromString(uuid)))!!
    }
}
