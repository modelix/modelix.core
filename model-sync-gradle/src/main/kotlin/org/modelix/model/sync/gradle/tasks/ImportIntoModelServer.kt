package org.modelix.model.sync.gradle.tasks

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
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.ModelImporter
import org.modelix.model.sync.importFilesAsRootChildren
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
        val branch = runBlocking {
            client.init()
            client.getReplicatedModel(branchRef).start()
        }

        val files = inputDir.listFiles()?.filter { it.extension == "json" } ?: error("no json files found")

        branch.runWrite {
            val rootNode = branch.getRootNode()
            println("Got root node: $rootNode")
            println("Importing...")
            ModelImporter(branch.getRootNode()).importFilesAsRootChildren(*files.toTypedArray())
            println("Import finished")
        }
    }
}
