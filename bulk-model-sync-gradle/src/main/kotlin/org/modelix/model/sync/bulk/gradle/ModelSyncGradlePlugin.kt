package org.modelix.model.sync.bulk.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.modelix.gradle.mpsbuild.MPSBuildPlugin
import org.modelix.model.sync.bulk.gradle.config.LocalSource
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.ModelSyncGradleSettings
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.ServerTarget
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import org.modelix.model.sync.bulk.gradle.tasks.ExportFromModelServer
import org.modelix.model.sync.bulk.gradle.tasks.GetRevisionInfo
import org.modelix.model.sync.bulk.gradle.tasks.ImportIntoModelServer
import org.modelix.model.sync.bulk.gradle.tasks.ValidateSyncSettings
import java.io.File
import java.util.Properties

class ModelSyncGradlePlugin : Plugin<Project> {

    private lateinit var settings: ModelSyncGradleSettings
    private lateinit var mpsBuildPlugin: MPSBuildPlugin
    private lateinit var mpsDependencies: Configuration

    override fun apply(project: Project) {
        mpsBuildPlugin = project.plugins.apply(MPSBuildPlugin::class.java)

        settings = project.extensions.create("modelSync", ModelSyncGradleSettings::class.java)
        getBaseDir(project).mkdirs()

        project.afterEvaluate {
            val validateSyncSettings =
                project.tasks.register("validateSyncSettings", ValidateSyncSettings::class.java) {
                    settings.taskDependencies.forEach { dependency ->
                        it.dependsOn(dependency)
                    }
                    it.settings.set(settings)
                }
            val modelixCoreVersion = readModelixCoreVersion()
                ?: throw RuntimeException("modelix.core version not found. Try running the writeVersionFile task.")

            mpsDependencies = project.configurations.create("modelSyncMpsDependencies")
            project.dependencies.add(
                mpsDependencies.name,
                "org.modelix:bulk-model-sync-mps:$modelixCoreVersion",
            )

            settings.syncDirections.forEach {
                registerTasksForSyncDirection(it, project, validateSyncSettings)
            }
        }
    }

    private fun registerTasksForSyncDirection(
        syncDirection: SyncDirection,
        project: Project,
        previousTask: TaskProvider<*>,
    ) {
        val baseDir = project.layout.buildDirectory.dir("model-sync").get().asFile.apply { mkdirs() }
        val jsonDir = baseDir.resolve(syncDirection.name).apply { mkdir() }
        val source = syncDirection.source

        val sourceTask = when (source) {
            is LocalSource -> registerTasksForLocalSource(syncDirection, previousTask, jsonDir)
            is ServerSource -> {
                if (source.baseRevision != null) {
                    previousTask
                } else {
                    registerTasksForServerSource(syncDirection, project, previousTask, jsonDir)
                }
            }
            else -> error("Unknown sync direction source")
        }

        when (syncDirection.target) {
            is LocalTarget -> registerTasksForLocalTarget(syncDirection, project, sourceTask, jsonDir)
            is ServerTarget -> registerTasksForServerTarget(syncDirection, project, sourceTask, jsonDir)
        }
    }

    private fun registerTasksForServerSource(
        syncDirection: SyncDirection,
        project: Project,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ): TaskProvider<*> {
        val serverSource = syncDirection.source as ServerSource
        val revisionFile = jsonDir.resolve(".modelix_revision").also { it.createNewFile() }

        val getRevisionInfo = project.tasks.register(
            "${syncDirection.name}GetRevisionInfo",
            GetRevisionInfo::class.java,
        ) {
            it.dependsOn(previousTask)

            it.serverUrl.set(serverSource.url)
            it.revision.set(serverSource.revision)
            it.repositoryId.set(serverSource.repositoryId)
            it.branchName.set(serverSource.branchName)
            it.revisionFile.set(revisionFile)

            it.outputs.upToDateWhen { serverSource.revision != null }
        }

        val exportFromModelServer = project.tasks.register(
            "${syncDirection.name}ExportFromModelServer",
            ExportFromModelServer::class.java,
        ) {
            it.dependsOn(getRevisionInfo)
            it.url.set(serverSource.url)
            it.repositoryId.set(serverSource.repositoryId)
            it.revisionFile.set(revisionFile)
            it.outputDir.set(jsonDir)
            it.includedModules.set(syncDirection.includedModules)
            it.includedModulePrefixes.set(syncDirection.includedModulePrefixes)
            it.excludedModules.set(syncDirection.excludedModules)
            it.excludedModulePrefixes.set(syncDirection.excludedModulePrefixes)
            it.requestTimeoutSeconds.set(serverSource.requestTimeoutSeconds)
        }
        return exportFromModelServer
    }

    private fun registerTasksForLocalSource(
        syncDirection: SyncDirection,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ): TaskProvider<*> {
        val resolvedDependencies = mpsDependencies.incoming.files
        val config = buildMpsRunConfigurationForLocalSources(syncDirection, resolvedDependencies.toSet(), jsonDir)
        return mpsBuildPlugin.createRunMPSTask("${syncDirection.name}ExportFromMps", config, arrayOf(previousTask)).also {
            it.configure { task ->
                task.outputs.dir(jsonDir)
            }
        }
    }

    private fun registerTasksForServerTarget(
        syncDirection: SyncDirection,
        project: Project,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ) {
        val importTaskName = "${syncDirection.name}ImportIntoModelServer"
        val importIntoModelServer = project.tasks.register(importTaskName, ImportIntoModelServer::class.java) {
            it.dependsOn(previousTask)
            it.inputDir.set(jsonDir)
            val serverTarget = syncDirection.target as ServerTarget

            it.url.set(serverTarget.url)
            it.repositoryId.set(serverTarget.repositoryId)
            it.branchName.set(serverTarget.branchName)
            it.includedModules.set(syncDirection.includedModules)
            it.includedModulePrefixes.set(syncDirection.includedModulePrefixes)
            it.excludedModules.set(syncDirection.excludedModules)
            it.excludedModulePrefixes.set(syncDirection.excludedModulePrefixes)
            it.continueOnError.set(syncDirection.continueOnError)
            it.requestTimeoutSeconds.set(serverTarget.requestTimeoutSeconds)
            it.metaProperties.set(serverTarget.metaProperties)
        }

        project.tasks.register("runSync${syncDirection.name.replaceFirstChar { it.uppercaseChar() }}") {
            it.dependsOn(importIntoModelServer)
            it.group = "modelix"
        }
    }

    private fun registerTasksForLocalTarget(
        syncDirection: SyncDirection,
        project: Project,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ) {
        val resolvedDependencies = mpsDependencies.incoming.files
        val config = buildMpsRunConfigurationForLocalTarget(syncDirection, resolvedDependencies.toSet(), jsonDir)
        val importName = "${syncDirection.name}ImportIntoMps"
        val importIntoMps = mpsBuildPlugin.createRunMPSTask(importName, config, arrayOf(previousTask)).also {
            it.configure { task ->
                task.inputs.dir(jsonDir)
            }
        }

        project.tasks.register("runSync${syncDirection.name.replaceFirstChar { it.uppercaseChar() }}") {
            it.dependsOn(importIntoMps)
            it.group = "modelix"
        }
    }

    private fun getBaseDir(project: Project): File {
        return project.layout.buildDirectory.dir("model-sync").get().asFile
    }

    private fun readModelixCoreVersion(): String? {
        val resources = javaClass.classLoader.getResources("modelix.core.version.properties") ?: return null
        if (resources.hasMoreElements()) {
            val properties = resources.nextElement().openStream().use { Properties().apply { load(it) } }
            return properties.getProperty("modelix.core.version")
        }
        return null
    }
}
