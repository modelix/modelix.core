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
import org.modelix.mps.sync.mps.factories.SNodeFactory
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.addLanguageImport
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.SyncLock
import org.modelix.mps.sync.util.SyncQueue
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.isDevKitDependency
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
                logger.error("$javaClass exploded")
                ex.printStackTrace()
            }
        }
    }

    fun transformLanguageDependency(iNode: INode, onlyAddToParentModel: Boolean = false) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ)) {
            val moduleId = iNode.getModule()?.nodeIdAsLong()
            val parentModule = nodeMap.getModule(moduleId)!!
            val dependentModule = getDependentModule(iNode, parentModule)

            val languageModuleReference = (dependentModule as Language).moduleReference
            val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference)
            val version =
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)

            // modelix does not store to which model the dependency belongs, thus adding it to all of them
            val languageVersion = version!!.toInt()
            if (!onlyAddToParentModel) {
                parentModule.models.forEach {
                    it.addLanguageImport(sLanguage, languageVersion)
                    nodeMap.put(it, languageModuleReference, iNode.nodeIdAsLong())
                }
            } else {
                val modelNodeId = iNode.getModel()?.nodeIdAsLong()
                val parentModel = nodeMap.getModel(modelNodeId)!!
                parentModel.addLanguageImport(sLanguage, languageVersion)
                nodeMap.put(parentModel, languageModuleReference, iNode.nodeIdAsLong())
            }
        }
    }

    fun transformDevKitDependency(iNode: INode, onlyAddToParentModel: Boolean = false) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ)) {
            val moduleId = iNode.getModule()?.nodeIdAsLong()
            val parentModule = nodeMap.getModule(moduleId)!!
            val dependentModule = getDependentModule(iNode, parentModule)

            val devKitModuleReference = (dependentModule as DevKit).moduleReference

            // modelix does not store to which model the dependency belongs, thus adding it to all of them
            if (!onlyAddToParentModel) {
                parentModule.models.forEach {
                    it.addDevKit(devKitModuleReference)
                    nodeMap.put(it, devKitModuleReference, iNode.nodeIdAsLong())
                }
            } else {
                val modelNodeId = iNode.getModel()?.nodeIdAsLong()
                val parentModel = nodeMap.getModel(modelNodeId)!!
                parentModel.addDevKit(devKitModuleReference)
                nodeMap.put(parentModel, devKitModuleReference, iNode.nodeIdAsLong())
            }
        }
    }

    fun resolveReferences() = nodeFactory.resolveReferences()

    fun clearResolvableReferences() = nodeFactory.clearResolvableReferences()

    private fun getDependentModule(iNode: INode, parentModule: SModule): SModule {
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)!!
        return parentModule.repository?.getModule(ModuleId.regular(UUID.fromString(uuid)))!!
    }
}
