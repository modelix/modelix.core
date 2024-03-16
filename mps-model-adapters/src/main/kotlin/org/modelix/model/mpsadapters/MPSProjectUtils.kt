/*
 * Copyright (c) 2024.
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

package org.modelix.model.mpsadapters

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.MementoImpl
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.ProjectPathUtil
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.ModuleFacetDescriptor
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.ModuleDependencyVersions
import jetbrains.mps.smodel.language.LanguageRegistry
import jetbrains.mps.vfs.IFile
import org.jetbrains.mps.openapi.module.SModule
import java.io.File
import java.io.IOException

object MPSProjectUtils {
    fun createModule(mpsProject: MPSProject, nameSpace: String, moduleId: ModuleId, requestor: Any?): SModule {
        // A module may already exist in the global repository, but is just not part of the project yet.
        val existingModule: SModule? = mpsProject.repository.getModule(moduleId)
        if (existingModule != null) {
            mpsProject.addModule(existingModule)
            return existingModule
        }
        val moduleFolder: VirtualFile? = LocalFileSystem.getInstance().findFileByIoFile(File(mpsProject.projectFile, nameSpace))
        if (moduleFolder != null && moduleFolder.exists()) {
            try {
                moduleFolder.delete(requestor)
            } catch (e: IOException) {
                throw RuntimeException("Failed deleting $moduleFolder", e)
            }
        }
        val basePath = checkNotNull(mpsProject.project.basePath) { "project.basePath is null" }
        val descriptorFile: IFile = mpsProject.fileSystem.getFile(basePath)
            .findChild(nameSpace).findChild(nameSpace + MPSExtentions.DOT_SOLUTION)
        val descriptor: SolutionDescriptor = createNewSolutionDescriptor(nameSpace, descriptorFile)
        descriptor.id = moduleId
        val module: Solution = GeneralModuleFactory().instantiate(descriptor, descriptorFile) as Solution
        mpsProject.addModule(module)
        ModuleDependencyVersions(
            LanguageRegistry.getInstance(mpsProject.repository),
            mpsProject.repository,
        ).update(module)
        module.save()
        return module
    }

    private fun createNewSolutionDescriptor(namespace: String, descriptorFile: IFile): SolutionDescriptor {
        val descriptor = SolutionDescriptor()
        descriptor.namespace = namespace
        descriptor.id = ModuleId.regular()
        val moduleLocation = checkNotNull(descriptorFile.parent) { "$descriptorFile not inside a folder" }
        val modelsDir: IFile = moduleLocation.findChild("models")
        check(!modelsDir.exists() || (modelsDir.children?.size ?: 0) == 0) {
            "Attempted creation of a solution inside an existing solution's directory: $moduleLocation"
        }
        modelsDir.mkdirs()
        descriptor.modelRootDescriptors.add(DefaultModelRoot.createDescriptor(moduleLocation, modelsDir))
        descriptor.moduleFacetDescriptors.add(ModuleFacetDescriptor("java", MementoImpl()))
        ProjectPathUtil.setGeneratorOutputPath(descriptor, moduleLocation.findChild("source_gen").path)
        return descriptor
    }
}
