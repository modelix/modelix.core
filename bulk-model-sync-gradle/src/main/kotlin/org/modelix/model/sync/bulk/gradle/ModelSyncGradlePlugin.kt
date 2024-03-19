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

package org.modelix.model.sync.bulk.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.modelix.buildtools.runner.MPSRunnerConfig
import org.modelix.gradle.mpsbuild.MPSBuildPlugin
import org.modelix.model.sync.bulk.gradle.config.LocalSource
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.ModelSyncGradleSettings
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.ServerTarget
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import org.modelix.model.sync.bulk.gradle.tasks.ExportFromModelServer
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
        val sourceTask = when (syncDirection.source) {
            is LocalSource -> registerTasksForLocalSource(syncDirection, previousTask, jsonDir)
            is ServerSource -> registerTasksForServerSource(syncDirection, project, previousTask, jsonDir)
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

        val name = "${syncDirection.name}ExportFromModelServer"
        val exportFromModelServer = project.tasks.register(name, ExportFromModelServer::class.java) {
            it.dependsOn(previousTask)
            it.outputDir.set(jsonDir)
            it.url.set(serverSource.url)
            it.repositoryId.set(serverSource.repositoryId)
            it.branchName.set(serverSource.branchName)
            it.revision.set(serverSource.revision)
            it.includedModules.set(syncDirection.includedModules)
            it.includedModulePrefixes.set(syncDirection.includedModulePrefixes)
            it.requestTimeoutSeconds.set(serverSource.requestTimeoutSeconds)
        }
        return exportFromModelServer
    }

    private fun registerTasksForLocalSource(
        syncDirection: SyncDirection,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ): TaskProvider<*> {
        val localSource = syncDirection.source as LocalSource
        val resolvedDependencies = mpsDependencies.resolvedConfiguration.files
        val config = MPSRunnerConfig(
            mainClassName = "org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer",
            mainMethodName = "exportRepository",
            classPathElements = resolvedDependencies.toList(),
            mpsHome = localSource.mpsHome,
            workDir = jsonDir,
            additionalModuleDirs = localSource.mpsLibraries.toList() + listOfNotNull(localSource.repositoryDir),
            jvmArgs = listOfNotNull(
                "-Dmodelix.mps.model.sync.bulk.output.path=${jsonDir.absolutePath}",
                "-Dmodelix.mps.model.sync.bulk.output.modules=${syncDirection.includedModules.joinToString(",")}",
                "-Dmodelix.mps.model.sync.bulk.output.modules.prefixes=${syncDirection.includedModulePrefixes.joinToString(",")}",
                "-Dmodelix.mps.model.sync.bulk.repo.path=${localSource.repositoryDir?.absolutePath}",
                "-Xmx${localSource.mpsHeapSize}",
                localSource.mpsDebugPort?.let { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$it" },
            ),
        )
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
        val localTarget = syncDirection.target as LocalTarget
        val importName = "${syncDirection.name}ImportIntoMps"
        val resolvedDependencies = mpsDependencies.resolvedConfiguration.files
        val config = MPSRunnerConfig(
            mainClassName = "org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer",
            mainMethodName = "importRepository",
            classPathElements = resolvedDependencies.toList(),
            mpsHome = localTarget.mpsHome,
            workDir = jsonDir,
            additionalModuleDirs = localTarget.mpsLibraries.toList() + listOfNotNull(localTarget.repositoryDir),
            jvmArgs = listOfNotNull(
                "-Dmodelix.mps.model.sync.bulk.input.path=${jsonDir.absolutePath}",
                "-Dmodelix.mps.model.sync.bulk.input.modules=${syncDirection.includedModules.joinToString(",")}",
                "-Dmodelix.mps.model.sync.bulk.input.modules.prefixes=${syncDirection.includedModulePrefixes.joinToString(",")}",
                "-Dmodelix.mps.model.sync.bulk.repo.path=${localTarget.repositoryDir?.absolutePath}",
                "-Dmodelix.mps.model.sync.bulk.input.continueOnError=${syncDirection.continueOnError}",
                "-Xmx${localTarget.mpsHeapSize}",
                localTarget.mpsDebugPort?.let { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$it" },
            ),
        )
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
