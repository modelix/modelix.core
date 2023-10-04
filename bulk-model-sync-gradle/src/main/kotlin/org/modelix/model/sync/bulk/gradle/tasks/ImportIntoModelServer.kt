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
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.modelix.model.ModelFacade
import org.modelix.model.api.ILanguage
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.importFilesAsRootChildren
import javax.inject.Inject

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
    val registeredLanguages: SetProperty<ILanguage> = of.setProperty(ILanguage::class.java)

    @TaskAction
    fun import() {
        registeredLanguages.get().forEach {
            ILanguageRepository.default.registerLanguage(it)
        }

        val inputDir = inputDir.get().asFile
        val repoId = RepositoryId(repositoryId.get())

        val branchRef = ModelFacade.createBranchReference(repoId, branchName.get())
        val client = ModelClientV2PlatformSpecificBuilder().url(url.get()).build()
        val files = inputDir.listFiles()?.filter { it.extension == "json" }
        if (files.isNullOrEmpty()) error("no json files found")

        runBlocking {
            client.init()
            client.runWrite(branchRef) { rootNode ->
                logger.info("Got root node: {}", rootNode)
                logger.info("Importing...")
                ModelImporter(rootNode).importFilesAsRootChildren(*files.toTypedArray())
                logger.info("Import finished")
            }
        }
    }
}
