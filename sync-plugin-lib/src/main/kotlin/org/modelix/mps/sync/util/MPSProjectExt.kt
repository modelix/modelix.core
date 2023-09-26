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

package org.modelix.mps.sync.util

import com.intellij.openapi.vfs.LocalFileSystem
import jetbrains.mps.ide.newSolutionDialog.NewModuleUtil
import jetbrains.mps.lang.migration.runtime.base.VersionFixer
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.vfs.IFile
import org.jetbrains.mps.openapi.module.SModule
import java.io.File
import java.io.IOException

// status: ready to test

fun MPSProject.createModule(nameSpace: String, moduleId: ModuleId, requestor: Any): SModule {
    // A module may already exist in the global repository, but is just not part of the project yet.
    val existingModule = repository.getModule(moduleId)
    if (existingModule != null) {
        addModule(existingModule)
        return existingModule
    }

    val moduleFolder = File(this.projectFile, nameSpace)
    val virtualModuleFolder = LocalFileSystem.getInstance().findFileByIoFile(moduleFolder)
    if (virtualModuleFolder?.exists() == true) {
        try {
            virtualModuleFolder.delete(requestor)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to delete $virtualModuleFolder", ex)
        }
    }

    // HACK: we are calling a private static method here
    val descriptorFile = ReflectionUtil.callStaticMethod(
        NewModuleUtil::class.java,
        "getModuleFile",
        arrayOf(
            String::class.java,
            String::class.java,
            String::class.java,
        ),
        arrayOf(nameSpace, moduleFolder.absolutePath, MPSExtentions.DOT_SOLUTION),
    ) as IFile?
    check(descriptorFile != null) { "descriptor file should not be null" }

    // HACK: we are calling a private static method here
    val descriptor = ReflectionUtil.callStaticMethod(
        NewModuleUtil::class.java,
        "createNewSolutionDescriptor",
        arrayOf(
            String::class.java,
            IFile::class.java,
        ),
        arrayOf(nameSpace, descriptorFile),
    ) as SolutionDescriptor
    descriptor.id = moduleId

    val module = GeneralModuleFactory().instantiate(descriptor, descriptorFile) as Solution
    this.addModule(module)
    VersionFixer(this, module, false).updateImportVersions()
    module.save()
    return module
}
