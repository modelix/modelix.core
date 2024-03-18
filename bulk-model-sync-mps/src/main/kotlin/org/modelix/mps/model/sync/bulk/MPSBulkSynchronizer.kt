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
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.language.StructureRegistry
import jetbrains.mps.smodel.runtime.ConceptDescriptor
import jetbrains.mps.smodel.runtime.illegal.IllegalConceptDescriptor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.data.ModelData
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.sync.bulk.ExistingAndExpectedNode
import org.modelix.model.sync.bulk.ModelExporter
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.isModuleIncluded
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

object MPSBulkSynchronizer {

    @JvmStatic
    fun exportRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules"))
        val includedModulePrefixes =
            parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.prefixes"))

        repository.modelAccess.runReadAction {
            val allModules = repository.modules
            val includedModules = allModules.filter {
                isModuleIncluded(it.moduleName!!, includedModuleNames, includedModulePrefixes)
            }
            val numIncludedModules = includedModules.count()
            val outputPath = System.getProperty("modelix.mps.model.sync.bulk.output.path")
            val counter = AtomicInteger()

            includedModules.parallelStream().forEach { module ->
                val pos = counter.incrementAndGet()

                repository.modelAccess.runReadAction {
                    println("Exporting module $pos of $numIncludedModules: '${module.moduleName}'")
                    val exporter = ModelExporter(MPSModuleAsNode(module))
                    val outputFile = File(outputPath + File.separator + module.moduleName + ".json")
                    exporter.export(outputFile)
                    println("Exported module $pos of $numIncludedModules: '${module.moduleName}'")
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun importRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules"))
        val includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes"))
        val inputPath = System.getProperty("modelix.mps.model.sync.bulk.input.path")
        val continueOnError = System.getProperty("modelix.mps.model.sync.bulk.input.continueOnError", "false").toBoolean()
        val jsonFiles = File(inputPath).listFiles()?.filter {
            it.extension == "json" && isModuleIncluded(
                it.nameWithoutExtension,
                includedModuleNames,
                includedModulePrefixes,
            )
        }

        if (jsonFiles.isNullOrEmpty()) error("no json files found for included modules")

        println("Found ${jsonFiles.size} modules to be imported")
        val access = repository.modelAccess
        access.executeCommandInEDT {
            val allModules = repository.modules
            val includedModules: Iterable<SModule> = allModules.filter {
                isModuleIncluded(it.moduleName!!, includedModuleNames, includedModulePrefixes)
            }
            val numIncludedModules = includedModules.count()
            val repoAsNode = MPSRepositoryAsNode(repository)
            println("Importing modules...")
            try {
                println("Importing modules...")
                // `modulesToImport` lazily produces modules to import
                // so that loaded model data can be garbage collected.
                val modulesToImport = includedModules.asSequence().flatMapIndexed { index, module ->
                    println("Importing module ${index + 1} of $numIncludedModules: '${module.moduleName}'")
                    val fileName = inputPath + File.separator + module.moduleName + ".json"
                    val moduleFile = File(fileName)
                    if (moduleFile.exists()) {
                        val expectedData: ModelData = moduleFile.inputStream().use(Json::decodeFromStream)
                        sequenceOf(ExistingAndExpectedNode(MPSModuleAsNode(module), expectedData))
                    } else {
                        println("Skip importing ${module.moduleName}} because $fileName does not exist.")
                        sequenceOf()
                    }
                }
                ModelImporter(repoAsNode, continueOnError).importIntoNodes(modulesToImport)
                println("Import finished.")
            } catch (ex: Exception) {
                // Exceptions are only visible in the MPS log file by default
                ex.printStackTrace()
            }
            println("Import finished.")
        }

        ApplicationManager.getApplication().invokeAndWait {
            println("Persisting changes...")
            access.executeCommandInEDT {
                enableWorkaroundForFilePerRootPersistence(repository)
                repository.saveAll()
            }
            println("Changes persisted.")
        }
    }

    /**
     * Workaround for MPS not being able to read the name property of the node during the save process
     * in case FilePerRootPersistence is used.
     * This is because the concept is not properly loaded and in the MPS code it checks if the concept is a subconcept
     * of INamedConcept.
     * Without this workaround the id of the root node will be used instead of the name, resulting in renamed files.
     */
    @JvmStatic
    private fun enableWorkaroundForFilePerRootPersistence(repository: SRepository) {
        val structureRegistry: StructureRegistry = ConceptRegistry.getInstance().readField("myStructureRegistry")
        val myConceptDescriptorsById: MutableMap<SConceptId, ConceptDescriptor> = structureRegistry.readField("myConceptDescriptorsById")

        repository.modules
            .asSequence()
            .flatMap { it.models }
            .mapNotNull { it as? EditableSModel }
            .filter { it.isChanged }
            .flatMap { it.rootNodes }
            .mapNotNull { (it.concept as? SConceptAdapterById) }
            .forEach {
                myConceptDescriptorsById.putIfAbsent(it.id, DummyNamedConceptDescriptor(it))
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> Any.readField(name: String): R {
        return this::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(this) as R
    }

    private class DummyNamedConceptDescriptor(concept: SConceptAdapterById) : ConceptDescriptor by IllegalConceptDescriptor(concept.id, concept.qualifiedName) {
        override fun isAssignableTo(other: SConceptId?): Boolean {
            return MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept) == other
        }

        override fun getSuperConceptId(): SConceptId {
            return MetaIdHelper.getConcept(SNodeUtil.concept_BaseConcept)
        }

        override fun getAncestorsIds(): MutableSet<SConceptId> {
            return mutableSetOf(MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept))
        }

        override fun getParentsIds(): MutableList<SConceptId> {
            return mutableListOf(MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept))
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
