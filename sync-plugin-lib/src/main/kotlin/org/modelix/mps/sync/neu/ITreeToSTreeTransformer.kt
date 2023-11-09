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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VirtualFileManager
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.project.structure.modules.SolutionKind
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.VFSManager
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.util.createModel
import org.modelix.mps.sync.util.nodeIdAsLong
import java.io.File

class ITreeToSTreeTransformer(private val replicatedModel: ReplicatedModel, private val project: MPSProject) {

    private val sModuleById = mutableMapOf<String, SModule>()
    private val sModelById = mutableMapOf<String, SModel>()

    fun transform(): SNode? {
        try {
            // 1. Register the language concepts so they are ready for lookup
            val repository = project.repository
            val mpsLanguageRepo = MPSLanguageRepository(repository)
            ILanguageRepository.register(mpsLanguageRepo)

            // 2. Traverse and transform the tree
            // TODO use coroutines instead of big-bang eager loading?
            replicatedModel.getBranch().runReadT { transaction ->
                val allChildren = transaction.tree.getAllChildren(1L)

                println("Level: 1")
                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())!!
                    printNode(iNode)
                    traverse(iNode, 1)
                }
            }
        } catch (ex: Exception) {
            println("${this.javaClass} exploded")
            ex.printStackTrace()
        }

        return null
    }

    fun traverse(parent: INode, level: Int) {
        println("Level: $level")
        parent.allChildren.forEach {
            printNode(it)
            traverse(it, level + 1)
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

        transformNode(iNode)
    }

    private fun transformNode(iNode: INode) {
        val isModule = iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.Module.getUID()
        if (isModule) {
            transformToModule(iNode)
        }

        val isModel = iNode.concept?.getUID() == BuiltinLanguages.MPSRepositoryConcepts.Model.getUID()
        if (isModel) {
            addModelToModule(iNode)
        }
    }

    private fun transformToModule(iNode: INode) {
        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
        check(serializedId.isNotEmpty()) { "Module's ($iNode) ID is empty" }

        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Module's ($iNode) name is null" }

        val sModule = createModule(name, moduleId as ModuleId)
        sModuleById[serializedId] = sModule
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

        project.modelAccess.runWriteInEDT {
            val sModel = module.createModel(name, modelId) as EditableSModel
            module.repository?.modelAccess?.runWriteInEDT {
                sModel.save()
            }
            sModelById[serializedId] = sModel
        }
    }

    // TODO HACKY WAY TO CREATE A MODULE IN THE PROJECT (part 1)
    private fun createModule(name: String, moduleId: ModuleId): Solution {
        val inCheckoutMode = true // cloud -> mps
        val exportPath = project.projectFile.systemIndependentPath
        println("SYSTEM INDEPENDENT PATH: $exportPath")
        val coreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java,
        )
        val vfsManager = coreComponents.platform.findComponent(
            VFSManager::class.java,
        )
        val fileSystem = vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        val outputFolder: IFile = fileSystem.getFile(exportPath)

        if (!inCheckoutMode) {
            outputFolder.deleteIfExists()
        }
        val solutionFile = outputFolder.findChild(name).findChild("solution" + MPSExtentions.DOT_SOLUTION)
        val solutionDir = outputFolder.findChild(name)
        if (inCheckoutMode) {
            ApplicationManager.getApplication().invokeAndWait {
                VirtualFileManager.getInstance().syncRefresh()
                val modelsDirVirtual = solutionDir.findChild("models")
                ensureDirDeletionAndRecreation(modelsDirVirtual)
            }
        }
        val descriptor = SolutionDescriptor()
        descriptor.namespace = name

        descriptor.id = moduleId
        descriptor.modelRootDescriptors.add(
            DefaultModelRoot.createDescriptor(
                solutionFile.parent!!,
                solutionFile.parent!!
                    .findChild(Solution.SOLUTION_MODELS),
            ),
        )
        descriptor.setKind(SolutionKind.PLUGIN_OTHER)
        val solution = GeneralModuleFactory().instantiate(descriptor, solutionFile) as Solution
        project.addModule(solution)
        check(solution.repository != null) { "The solution should be in a repo, so also the model will be in a repo and syncReference will not crash" }

        return solution
    }

    // TODO HACKY WAY TO CREATE A MODULE IN THE PROJECT (part 2)
    /**
     * We experienced issues with physical and virtual files being out of sync.
     * This method ensure that files are deleted, recursively both on the virtual filesystem and the physical filesystem.
     */
    private fun ensureDeletion(virtualFile: IFile) {
        if (virtualFile.isDirectory) {
            virtualFile.children?.forEach { child ->
                ensureDeletion(child)
            }
        } else {
            if (virtualFile.exists()) {
                virtualFile.delete()
            }
            val physicalFile = File(virtualFile.path)
            physicalFile.delete()
        }
    }

    // TODO HACKY WAY TO CREATE A MODULE IN THE PROJECT (part 3)
    private fun ensureDirDeletionAndRecreation(virtualDir: IFile) {
        ensureDeletion(virtualDir)
        virtualDir.mkdirs()
    }
}
