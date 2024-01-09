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
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.factories.SNodeFactory
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.addLanguageImport
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.getModule
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeTransformer(
    private val modelAccess: ModelAccess,
    private val nodeMap: MpsToModelixMap,
    mpsLanguageRepository: MPSLanguageRepository,
) {

    private val logger = logger<NodeTransformer>()
    private val nodeFactory = SNodeFactory(mpsLanguageRepository, modelAccess, nodeMap)

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

    fun transformLanguageDependency(
        iNode: INode,
        mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking,
        onlyAddToParentModel: Boolean = false,
    ) {
        val moduleId = iNode.getModule()?.nodeIdAsLong()
        val parentModule = nodeMap.getModule(moduleId)!!
        val dependentModule = getDependentModule(iNode, parentModule)

        var languageModuleReference: SModuleReference? = null
        modelAccess.runReadBlocking {
            languageModuleReference = (dependentModule as Language).moduleReference
        }
        val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference!!)
        val version =
            iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)

        // modelix does not store to which model the dependency belongs, thus adding it to all of them
        mpsWriteAction {
            val languageVersion = version!!.toInt()
            if (!onlyAddToParentModel) {
                parentModule.models.forEach {
                    it.addLanguageImport(sLanguage, languageVersion)
                    // TODO this might not work, because if more than one models/modules point to the same Language, then the modelix ID will be always overwritten by the last Node (SingleLanguageDependency) that points to this Language
                    // TODO we might have to find a different traceability between the LanguageDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing LanguageDependencies in the cloud)
                    // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
                    nodeMap.put(languageModuleReference!!, iNode.nodeIdAsLong(), it)
                }
            } else {
                val modelNodeId = iNode.getModel()?.nodeIdAsLong()
                val parentModel = nodeMap.getModel(modelNodeId)!!
                parentModel.addLanguageImport(sLanguage, languageVersion)

                // TODO this might not work, because if more than one models/modules point to the same Language, then the modelix ID will be always overwritten by the last Node (SingleLanguageDependency) that points to this Language
                // TODO we might have to find a different traceability between the LanguageDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing LanguageDependencies in the cloud)
                // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
                nodeMap.put(languageModuleReference!!, iNode.nodeIdAsLong(), parentModel)
            }
        }
    }

    fun transformDevKitDependency(
        iNode: INode,
        mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking,
        onlyAddToParentModel: Boolean = false,
    ) {
        val moduleId = iNode.getModule()?.nodeIdAsLong()
        val parentModule = nodeMap.getModule(moduleId)!!
        val dependentModule = getDependentModule(iNode, parentModule)

        var devKitModuleReference: SModuleReference? = null
        modelAccess.runReadBlocking {
            devKitModuleReference = (dependentModule as DevKit).moduleReference
        }

        // modelix does not store to which model the dependency belongs, thus adding it to all of them
        mpsWriteAction {
            if (!onlyAddToParentModel) {
                parentModule.models.forEach {
                    it.addDevKit(devKitModuleReference!!)
                    // TODO this might not work, because if more than one models/modules point to the same DevKit, then the modelix ID will be always overwritten by the last Node (DevkitDependency) that points to this devkit
                    // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing DevKitDependencies in the cloud)
                    // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
                    nodeMap.put(devKitModuleReference!!, iNode.nodeIdAsLong(), it)
                }
            } else {
                val modelNodeId = iNode.getModel()?.nodeIdAsLong()
                val parentModel = nodeMap.getModel(modelNodeId)!!
                parentModel.addDevKit(devKitModuleReference!!)

                // TODO this might not work, because if more than one models/modules point to the same DevKit, then the modelix ID will be always overwritten by the last Node (DevkitDependency) that points to this devkit
                // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing DevKitDependencies in the cloud)
                // TODO store the moduleReference together with the model, because this composite key should be unique --> when deleting the moduleReference figure out the model as well and look for this composite key
                nodeMap.put(devKitModuleReference!!, iNode.nodeIdAsLong(), parentModel)
            }
        }
    }

    fun resolveReferences(mpsWriteAction: ((Runnable) -> Unit) = modelAccess::runWriteActionInEDTBlocking) =
        nodeFactory.resolveReferences(mpsWriteAction)

    fun clearResolvableReferences() = nodeFactory.clearResolvableReferences()

    private fun getDependentModule(iNode: INode, parentModule: SModule): SModule {
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
        val dependentModule = uuid?.let {
            val reference = AtomicReference<SModule>()
            modelAccess.runReadAction {
                reference.set(parentModule.repository?.getModule(ModuleId.regular(UUID.fromString(it))))
            }
            reference.get()
        }!!
        return dependentModule
    }
}
