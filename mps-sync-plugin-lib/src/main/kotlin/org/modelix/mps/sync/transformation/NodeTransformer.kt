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

package org.modelix.mps.sync.transformation

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.factories.SNodeFactory
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.addLanguageImport
import org.modelix.mps.sync.mps.util.runWriteInEDTBlocking
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeTransformer(
    private val project: MPSProject,
    private val nodeMap: MpsToModelixMap,
    mpsLanguageRepository: MPSLanguageRepository,
) {

    private val logger = logger<NodeTransformer>()
    private val nodeFactory = SNodeFactory(mpsLanguageRepository, project.modelAccess, nodeMap)

    fun transformToNode(iNode: INode) {
        // TODO figure out which model the iNode belongs to
        val model = nodeMap.models.firstOrNull()!!
        val repository = model.repository

        // DevKit or LanguageDependency
        val isDevKitDependency =
            iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency.getUID()
        val isLanguageDependency =
            iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.getUID()
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
        val dependentModule = uuid?.let {
            val reference = AtomicReference<SModule>()
            project.modelAccess.runReadAction {
                reference.set(repository?.getModule(ModuleId.regular(UUID.fromString(it))))
            }
            reference.get()
        }

        if (isDevKitDependency) {
            transformDevKitDependency(dependentModule, iNode, model)
        } else if (isLanguageDependency) {
            transformLanguageDependency(iNode, dependentModule, model)
        } else {
            try {
                nodeFactory.createNode(iNode, model)
            } catch (ex: Exception) {
                logger.error("$javaClass exploded")
                ex.printStackTrace()
            }
        }
    }

    private fun transformLanguageDependency(
        iNode: INode,
        dependentModule: SModule?,
        model: SModel,
    ) {
        val version =
            iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)
        val languageModuleReference = (dependentModule as Language).moduleReference

        // TODO this might not work, because if more than one models/modules point to the same Language, then the modelix ID will be always overwritten by the last Node (SingleLanguageDependency) that points to this Language
        // TODO we might have to find a different traceability between the LanguageDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing LanguageDependencies in the cloud)
        nodeMap.put(languageModuleReference, iNode.nodeIdAsLong())
        val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference)
        project.modelAccess.runWriteInEDTBlocking {
            model.addLanguageImport(sLanguage, version?.toInt()!!)
        }
    }

    private fun transformDevKitDependency(
        dependentModule: SModule?,
        iNode: INode,
        model: SModel,
    ) {
        project.modelAccess.runWriteInEDTBlocking {
            val devKitModuleReference = (dependentModule as DevKit).moduleReference

            // TODO this might not work, because if more than one models/modules point to the same DevKit, then the modelix ID will be always overwritten by the last Node (DevkitDependency) that points to this devkit
            // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing DevKitDependencies in the cloud)
            nodeMap.put(devKitModuleReference, iNode.nodeIdAsLong())

            model.addDevKit(devKitModuleReference)
        }
    }

    fun resolveReferences() = nodeFactory.resolveReferences()
}
