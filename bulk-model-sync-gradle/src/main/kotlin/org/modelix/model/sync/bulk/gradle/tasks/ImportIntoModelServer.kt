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

package org.modelix.model.sync.bulk.gradle.tasks

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.modelix.model.ModelFacade
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.importFilesAsRootChildren
import org.modelix.model.sync.bulk.isModuleIncluded
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

abstract class ImportIntoModelServer @Inject constructor(of: ObjectFactory) : DefaultTask() {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val inputDir: DirectoryProperty = of.directoryProperty()

    @Input
    val repositoryId: Property<String> = of.property(String::class.java)

    @Input
    val branchName: Property<String> = of.property(String::class.java)

    @Input
    val url: Property<String> = of.property(String::class.java)

    @Input
    val includedModules: SetProperty<String> = of.setProperty(String::class.java)

    @Input
    val includedModulePrefixes: SetProperty<String> = of.setProperty(String::class.java)

    @Input
    val continueOnError: Property<Boolean> = of.property(Boolean::class.java)

    @Input
    val requestTimeoutSeconds: Property<Int> = of.property(Int::class.java)

    @Input
    val metaProperties: MapProperty<String, String> = of.mapProperty(String::class.java, String::class.java)

    @TaskAction
    fun import() {
        val inputDir = inputDir.get().asFile
        val repoId = RepositoryId(repositoryId.get())

        val branchRef = ModelFacade.createBranchReference(repoId, branchName.get())
        val client = ModelClientV2.builder()
            .url(url.get())
            .requestTimeout(requestTimeoutSeconds.get().seconds)
            .build()
        val files = inputDir.listFiles()?.filter {
            it.extension == "json" && isModuleIncluded(it.nameWithoutExtension, includedModules.get(), includedModulePrefixes.get())
        }
        if (files.isNullOrEmpty()) error("no json files found for included modules")

        runBlocking {
            logger.info("Initializing client...")
            client.init()
            logger.info("Importing...")
            client.runWrite(branchRef) { rootNode ->
                rootNode.runBulkUpdate {
                    logger.info("Got root node: {}", rootNode)
                    logger.info("Calculating diff...")
                    ModelImporter(rootNode, continueOnError.get()).importFilesAsRootChildren(files)

                    logger.info("Setting meta properties...")
                    for ((key, value) in metaProperties.get()) {
                        rootNode.setPropertyValue(IProperty.fromName(key), value)
                    }
                }
                logger.info("Sending diff to server...")
            }
        }
        logger.info("Import finished.")
    }
}

/**
 * Memory optimization that doesn't record individual change operations, but only the result.
 */
private fun INode.runBulkUpdate(body: () -> Unit) {
    ((this as PNodeAdapter).branch as OTBranch).runBulkUpdate(body = body)
}
