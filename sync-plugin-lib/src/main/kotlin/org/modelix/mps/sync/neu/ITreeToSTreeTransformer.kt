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

package org.modelix.mps.sync.neu

import jetbrains.mps.project.DevKit
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.SModelReference
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.neu.listeners.NodeChangeListener
import org.modelix.mps.sync.util.addDevKit
import org.modelix.mps.sync.util.addLanguageImport
import org.modelix.mps.sync.util.createModel
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class ITreeToSTreeTransformer(private val replicatedModel: ReplicatedModel, private val project: MPSProject) {

    private val solutionProducer = SolutionProducer(project)

    private val nodeMap = MpsToModelixMap()

    // TODO may be replaced by nodeMap
    private val sModuleById = mutableMapOf<String, SModule>()

    // TODO may be replaced by nodeMap
    private val sModelById = mutableMapOf<String, SModel>()

    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()

    fun transform(): SNode? {
        try {
            // 1. Register the language concepts so they are ready for lookup
            val repository = project.repository
            val mpsLanguageRepo = MPSLanguageRepository(repository)
            ILanguageRepository.register(mpsLanguageRepo)

            // 2. Traverse and transform the tree
            // TODO use coroutines instead of big-bang eager loading?
            replicatedModel.getBranch().runReadT { transaction ->
                val sNodeFactory = SNodeFactory(mpsLanguageRepo, project.modelAccess, nodeMap)

                val allChildren = transaction.tree.getAllChildren(1L)

                println("--- PRINTING TREE ---")
                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())!!
                    traverse(iNode, 1) { }
                }

                println("--- FILTERING MODULES AND MODELS ---")
                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())!!
                    traverse(iNode, 1) { transformModulesAndModels(it) }
                }

                println("--- TRANSFORMING NODES ---")
                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())!!
                    traverse(iNode, 1) { transformNode(it, sNodeFactory) }
                }

                println("--- RESOLVING REFERENCES AND MODEL IMPORTS ---")
                sNodeFactory.resolveReferences()
                resolveModelImports(repository)

                println("--- REGISTER LISTENERS, AKA \"ACTIVATE BINDINGS\"")
                sModelById.values.forEach {
                    it.addChangeListener(NodeChangeListener(it, replicatedModel, nodeMap))
                }
            }
        } catch (ex: Exception) {
            println("${this.javaClass} exploded")
            ex.printStackTrace()
        }

        return null
    }

    private fun traverse(parent: INode, level: Int, processNode: (INode) -> Unit) {
        println("Level: $level")
        printNode(parent)
        processNode(parent)
        parent.allChildren.forEach {
            traverse(it, level + 1, processNode)
        }
    }

    private fun printNode(iNode: INode) {
        println()
        println("Node: $iNode")
        println("ID: ${iNode.nodeIdAsLong()}")
        println("Concept: ${iNode.concept?.getLongName()}")
        println("Properties:")
        iNode.getAllProperties().forEach {
            println("\t Property (${it.first.name}): " + it.first)
            println("\t Value: ${it.second}")
            println()
        }

        println("References:")

        iNode.getAllReferenceTargetRefs().forEach {
            println("\t Reference (${it.first.name}): " + it.first)
            println("\t Target: ${it.second.serialize()}")
            println()
        }
    }

    private fun transformModulesAndModels(iNode: INode) {
        val isModule = iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.Module.getUID()
        if (isModule) {
            transformToModule(iNode)
            return
        }

        val isModel = iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.Model.getUID()
        if (isModel) {
            addModelToModule(iNode)
            return
        }
    }

    private fun transformNode(iNode: INode, nodeFactory: SNodeFactory) {
        // TODO figure out which model the iNode belongs to
        val model = sModelById.values.firstOrNull()!!
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
            project.modelAccess.runWriteInEDT {
                val devKitModuleReference = (dependentModule as DevKit).moduleReference

                // TODO this might not work, because if more than one models/modules point to the same DevKit, then the modelix ID will be always overwritten by the last Node (DevkitDependency) that points to this devkit
                // TODO we might have to find a different traceability between the DevKitDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing DevKitDependencies in the cloud)
                nodeMap.put(devKitModuleReference, iNode.nodeIdAsLong())

                model.addDevKit(devKitModuleReference)
            }
        } else if (isLanguageDependency) {
            val version =
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)
            val languageModuleReference = (dependentModule as Language).moduleReference

            // TODO this might not work, because if more than one models/modules point to the same Language, then the modelix ID will be always overwritten by the last Node (SingleLanguageDependency) that points to this Language
            // TODO we migth have to find a different traceability between the LanguageDependency and the ModuleReference, so it works in the inverse direction too (in the ModelChangeListener, when adding/removing LanguageDependencies in the cloud)
            nodeMap.put(languageModuleReference, iNode.nodeIdAsLong())
            val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference)
            project.modelAccess.runWriteInEDT {
                model.addLanguageImport(sLanguage, version?.toInt()!!)
            }
        } else {
            try {
                nodeFactory.createNode(iNode, model)
            } catch (ex: Exception) {
                println("transformNode(...) exploded")
                ex.printStackTrace()
            }
        }
    }

    private fun transformToModule(iNode: INode) {
        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
        check(serializedId.isNotEmpty()) { "Module's ($iNode) ID is empty" }

        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Module's ($iNode) name is null" }

        val sModule = solutionProducer.createOrGetModule(name, moduleId as ModuleId)
        sModuleById[serializedId] = sModule

        // TODO shall we transform the ModuleDependencies here? module.addDependency(sModuleReference, reexport)
    }

    private fun addModelToModule(iNode: INode) {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Module's ($iNode) name is null" }

        val moduleId: String? = iNode.parent?.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)
        val module: SModule? = sModuleById[moduleId]
        check(module != null) { "Parent module with ID $moduleId is not found" }

        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
        check(serializedId.isNotEmpty()) { "Model's ($iNode) ID is empty" }
        val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

        lateinit var sModel: EditableSModel
        val latch = CountDownLatch(1)
        project.modelAccess.runWriteInEDT {
            sModel = module.createModel(name, modelId) as EditableSModel
            sModel.save()
            latch.countDown()
        }
        latch.await()
        sModelById[serializedId] = sModel
        nodeMap.put(sModel, iNode.nodeIdAsLong())

        // register model imports
        iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).forEach {
            val targetModel = it.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
            val targetId = targetModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)!!
            resolvableModelImports.add(
                ResolvableModelImport(
                    source = sModel,
                    targetModelId = targetId,
                    targetModelModelixId = targetModel.nodeIdAsLong(),
                    modelReferenceNodeId = it.nodeIdAsLong(),
                ),
            )
        }
    }

    private fun resolveModelImports(repository: SRepository) {
        resolvableModelImports.forEach {
            val target = it.targetModelId
            val id = PersistenceFacade.getInstance().createModelId(target)
            val targetModel = sModelById.getOrElse(target) { repository.getModel(id) }!!
            nodeMap.put(targetModel, it.targetModelModelixId)

            val targetModule = targetModel.module
            val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
            val modelImport = SModelReference(moduleReference, id, targetModel.name)

            nodeMap.put(modelImport, it.modelReferenceNodeId)
            ModelImports(it.source).addModelImport(modelImport)
        }
    }
}

data class ResolvableModelImport(
    val source: SModel,
    val targetModelId: String,
    val targetModelModelixId: Long,
    val modelReferenceNodeId: Long,
)
