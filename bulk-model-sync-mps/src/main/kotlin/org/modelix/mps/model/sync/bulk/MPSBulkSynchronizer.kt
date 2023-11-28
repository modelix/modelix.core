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

package org.modelix.mps.model.sync.bulk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import jetbrains.mps.ide.project.ProjectHelper
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.sync.bulk.ModelExporter
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.import
import org.modelix.model.sync.bulk.isModuleIncluded
import java.io.File

object MPSBulkSynchronizer {

    @JvmStatic
    fun exportRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules"))
        val includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.prefixes"))

        repository.modelAccess.runReadAction {
            val allModules = repository.modules
            val includedModules = allModules.filter {
                isModuleIncluded(it.moduleName!!, includedModuleNames, includedModulePrefixes)
            }
            val numIncludedModules = includedModules.count()
            val outputPath = System.getProperty("modelix.mps.model.sync.bulk.output.path")

            for ((index, module) in includedModules.withIndex()) {
                println("Exporting module ${index + 1} of $numIncludedModules: '${module.moduleName}'")
                val exporter = ModelExporter(MPSModuleAsNode(module))
                val outputFile = File(outputPath + File.separator + module.moduleName + ".json")
                exporter.export(outputFile)
            }
        }
    }

    @JvmStatic
    fun importRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules"))
        val includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes"))
        val inputPath = System.getProperty("modelix.mps.model.sync.bulk.input.path")
        val access = repository.modelAccess

        access.runWriteInEDT {
            val allModules = repository.modules
            val includedModules: Iterable<SModule> = allModules.filter {
                isModuleIncluded(it.moduleName!!, includedModuleNames, includedModulePrefixes)
            }
            val numIncludedModules = includedModules.count()

            access.executeCommand {
                for ((index, module) in includedModules.withIndex()) {
                    println("Importing module ${index + 1} of $numIncludedModules: '${module.moduleName}'")
                    val moduleFile = File(inputPath + File.separator + module.moduleName + ".json")
                    if (moduleFile.exists()) {
                        val importer = ModelImporter(
                            MPSModuleAsNode(module),
                        )
                        importer.import(moduleFile)
                    }
                }
            }
        }

        ApplicationManager.getApplication().invokeAndWait {
            repository.modelAccess.runWriteAction {
                repository.saveAll()
            }
        }
    }

    @JvmStatic
    private fun parseRawPropertySet(rawProperty: String): Set<String> {
        return if (rawProperty.isEmpty()) {
            emptySet()
        } else {
            rawProperty.split(",")
                .dropLastWhile { it.isEmpty() }
                .toSet()
        }
    }

    @JvmStatic
    private fun getRepository(): SRepository {
        val repoPath = System.getProperty("modelix.mps.model.sync.bulk.repo.path")
        val project = ProjectManager.getInstance().loadAndOpenProject(repoPath)
        val repo = ProjectHelper.getProjectRepository(project)

        return checkNotNull(repo) { "project repository not found" }
    }
}
