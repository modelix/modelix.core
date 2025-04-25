package org.modelix.model.sync.bulk.gradle.tasks

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.ModelExporter
import org.modelix.model.sync.bulk.isModuleIncluded
import kotlin.time.Duration.Companion.seconds

abstract class ExportFromModelServer : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    @get:Optional
    abstract val repositoryId: Property<String>

    @get:InputFile
    abstract val revisionFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val includedModules: SetProperty<String>

    @get:Input
    abstract val includedModulePrefixes: SetProperty<String>

    @get:Input
    abstract val excludedModules: SetProperty<String>

    @get:Input
    abstract val excludedModulePrefixes: SetProperty<String>

    @get:Input
    abstract val requestTimeoutSeconds: Property<Int>

    @TaskAction
    fun export() = runBlocking {
        val modelClient = ModelClientV2PlatformSpecificBuilder()
            .url(url.get())
            .requestTimeout(requestTimeoutSeconds.get().seconds)
            .lazyAndBlockingQueries()
            .build()

        val revision = revisionFile.get().asFile.readText()

        modelClient.use { client ->
            client.init()
            val version = if (repositoryId.isPresent) {
                client.loadVersion(RepositoryId(repositoryId.get()), revision, null)
            } else {
                logger.warn("Specifying a repositoryId will be mandatory in the future.")
                client.loadVersion(revision, null)
            }

            val branch = PBranch(version.getTree(), client.getIdGenerator())

            branch.runRead {
                val root = branch.getRootNode()
                logger.info("Got root node: {}", root)
                val outputDir = outputDir.get().asFile.toPath()

                getIncludedModules(root).forEach {
                    val fileName = it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                    val outputFile = outputDir.resolve("$fileName.json")
                    ModelExporter(it).export(outputFile)
                }
            }
        }
    }

    private fun getIncludedModules(root: INode): Iterable<INode> {
        val nameRole = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name

        return root.allChildren.filter {
            val isModule = it.concept?.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module) == true
            val moduleName = it.getPropertyValue(nameRole) ?: return@filter false
            val isIncluded = isModuleIncluded(
                moduleName,
                includedModules.get(),
                includedModulePrefixes.get(),
                excludedModules.get(),
                excludedModulePrefixes.get(),
            )

            isModule && isIncluded
        }
    }
}
