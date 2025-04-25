package org.modelix.model.sync.bulk.gradle.tasks

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId

/**
 * Determines which revision needs to be pulled from the model server.
 * The determined revision is written to [revisionFile].
 */
abstract class GetRevisionInfo : DefaultTask() {

    @get:Input
    abstract val serverUrl: Property<String>

    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val branchName: Property<String>

    @get:Input
    @get:Optional
    abstract val revision: Property<String>

    @get:OutputFile
    abstract val revisionFile: RegularFileProperty

    @TaskAction
    fun getRevisionInfo() {
        val revision = determineRevision()
        logger.info("Revision to be synced: $revision")
        revisionFile.get().asFile.writeText(revision)
    }

    private fun determineRevision(): String {
        if (revision.isPresent) {
            return revision.get()
        }

        logger.info("Pulling versionHash from server...")
        val modelClient = ModelClientV2.builder().url(serverUrl.get()).lazyAndBlockingQueries().build()
        val branchRef = RepositoryId(repositoryId.get()).getBranchReference(branchName.get())

        return runBlocking {
            modelClient.use {
                it.init()
                it.pullHash(branchRef)
            }
        }
    }
}
