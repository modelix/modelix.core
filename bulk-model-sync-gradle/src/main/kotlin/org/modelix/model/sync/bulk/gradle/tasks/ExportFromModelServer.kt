/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.modelix.model.ModelFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.ModelExporter
import javax.inject.Inject

abstract class ExportFromModelServer @Inject constructor(of: ObjectFactory) : DefaultTask() {

    @Input
    val url: Property<String> = of.property(String::class.java)

    @Input
    @Optional
    val repositoryId: Property<String> = of.property(String::class.java)

    @Input
    @Optional
    val branchName: Property<String> = of.property(String::class.java)

    @Input
    @Optional
    val revision: Property<String> = of.property(String::class.java)

    @OutputDirectory
    val outputDir: DirectoryProperty = of.directoryProperty()

    @TaskAction
    fun export() {
        val client = ModelClientV2PlatformSpecificBuilder()
            .url(url.get())
            .build()

        runBlocking { client.init() }

        val branch = if (revision.isPresent) {
            getBranchByRevision(client)
        } else {
            getBranchByRepoIdAndBranch(client)
        }

        branch.runRead {
            val root = branch.getRootNode()
            logger.info("Got root node: {}", root)
            val outputDir = outputDir.get().asFile
            root.allChildren.forEach {
                val nameRole = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
                val fileName = it.getPropertyValue(nameRole)
                val outputFile = outputDir.resolve("$fileName.json")
                ModelExporter(it).export(outputFile)
            }
        }
    }

    private fun getBranchByRepoIdAndBranch(client: ModelClientV2): IBranch {
        val repoId = RepositoryId(repositoryId.get())
        val branchRef = ModelFacade.createBranchReference(repoId, branchName.get())

        val branch = runBlocking {
            client.getReplicatedModel(branchRef).start()
        }
        return branch
    }

    private fun getBranchByRevision(client: IModelClientV2): IBranch {
        val version = runBlocking { client.loadVersion(revision.get(), null) }
        return PBranch(version.getTree(), client.getIdGenerator())
    }
}
