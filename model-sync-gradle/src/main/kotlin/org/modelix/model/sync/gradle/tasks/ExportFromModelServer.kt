package org.modelix.model.sync.gradle.tasks

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
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.ModelExporter
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
        val client = ModelClientV2PlatformSpecificBuilder().url(url.get()).build().apply { runBlocking { init() } }
        val branch = if (revision.isPresent) {
            getBranchByRevision(client)
        } else {
            getBranchByRepoIdAndBranch(client)
        }

        branch.runRead {
            val root = branch.getRootNode()
            println("Got root node: $root")
            val outputFile = outputDir.get().asFile.resolve("exported-repo.json")
            ModelExporter(root).export(outputFile)
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
