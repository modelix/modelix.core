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

package org.modelix.mps.sync.mps.factories

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VirtualFileManager
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.project.structure.modules.SolutionKind
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.VFSManager
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import java.io.File

// TODO hacky solution. A nicer one could be: https://github.com/JetBrains/MPS/blob/master/workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/jetbrains/mps/project/modules/SolutionProducer.java
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SolutionProducer {

    private val project
        get() = ActiveMpsProjectInjector.activeMpsProject!!

    fun createOrGetModule(name: String, moduleId: ModuleId): Solution {
        val exportPath = project.projectFile.systemIndependentPath

        val coreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java,
        )
        val vfsManager = coreComponents.platform.findComponent(
            VFSManager::class.java,
        )
        val fileSystem = vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        val outputFolder: IFile = fileSystem.getFile(exportPath)

        val solutionFile = outputFolder.findChild(name).findChild("solution" + MPSExtentions.DOT_SOLUTION)
        val solutionDir = outputFolder.findChild(name)

        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().syncRefresh()
            val modelsDirVirtual = solutionDir.findChild("models")
            ensureDirDeletionAndRecreation(modelsDirVirtual)
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
        descriptor.kind = SolutionKind.PLUGIN_OTHER
        val solution = GeneralModuleFactory().instantiate(descriptor, solutionFile) as Solution
        project.addModule(solution)

        if (solution.repository == null) {
            // this might be a silly workaround...
            solution.attach(project.repository)
        }
        check(solution.repository != null) { "The solution should be in a repo, so also the model will be in a repo and syncReference will not crash" }

        return solution
    }

    private fun ensureDirDeletionAndRecreation(virtualDir: IFile) {
        ensureDeletion(virtualDir)
        virtualDir.mkdirs()
    }

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
}
