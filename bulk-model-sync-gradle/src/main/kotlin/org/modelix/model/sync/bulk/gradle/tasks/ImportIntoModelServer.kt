package org.modelix.model.sync.bulk.gradle.tasks

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
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
import kotlin.time.Duration.Companion.seconds

abstract class ImportIntoModelServer : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val branchName: Property<String>

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val includedModules: SetProperty<String>

    @get:Input
    abstract val includedModulePrefixes: SetProperty<String>

    @get:Input
    abstract val excludedModules: SetProperty<String>

    @get:Input
    abstract val excludedModulePrefixes: SetProperty<String>

    @get:Input
    abstract val continueOnError: Property<Boolean>

    @get:Input
    abstract val requestTimeoutSeconds: Property<Int>

    @get:Input
    abstract val metaProperties: MapProperty<String, String>

    @TaskAction
    fun import() = runBlocking {
        val inputDir = inputDir.get().asFile
        val repoId = RepositoryId(repositoryId.get())

        val branchRef = ModelFacade.createBranchReference(repoId, branchName.get())
        val files = inputDir.listFiles()?.filter {
            it.extension == "json" && isModuleIncluded(
                it.nameWithoutExtension,
                includedModules.get(),
                includedModulePrefixes.get(),
                excludedModules.get(),
                excludedModulePrefixes.get(),
            )
        }
        if (files.isNullOrEmpty()) error("no json files found for included modules")

        val client = ModelClientV2.builder()
            .url(url.get())
            .requestTimeout(requestTimeoutSeconds.get().seconds)
            .lazyAndBlockingQueries()
            .build()
        client.use {
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
