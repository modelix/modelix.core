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
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.modelix.model.sync.bulk.gradle.config.LocalSource
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.ModelSyncGradleSettings
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.ServerTarget
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import org.modelix.model.sync.bulk.gradle.tasks.ExportFromModelServer
import org.modelix.model.sync.bulk.gradle.tasks.ExportFromMps
import org.modelix.model.sync.bulk.gradle.tasks.GenerateAntScriptForMps
import org.modelix.model.sync.bulk.gradle.tasks.ImportIntoModelServer
import org.modelix.model.sync.bulk.gradle.tasks.ImportIntoMps
import org.modelix.model.sync.bulk.gradle.tasks.ValidateSyncSettings
import java.io.File
import java.util.Properties

class ModelSyncGradlePlugin : Plugin<Project> {

    private lateinit var settings: ModelSyncGradleSettings

    private val antDependenciesConfigName = "model-sync-ant-dependencies"

    override fun apply(project: Project) {
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
            val antDependencies = project.configurations.create(antDependenciesConfigName)
            project.dependencies.add(antDependencies.name, "org.apache.ant:ant-junit:1.10.12")

            val mpsDependencies = project.configurations.create("modelSyncMpsDependencies")
            project.dependencies.add(
                mpsDependencies.name,
                "org.modelix.mps:bulk-model-sync-solution:$modelixCoreVersion",
            )

            val copyMpsDependencies = project.tasks.register("copyMpsDependencies", Sync::class.java) { sync ->
                sync.dependsOn(validateSyncSettings)
                val src = mpsDependencies.resolve().map { project.zipTree(it) }
                val target = getDependenciesDir(project).apply { mkdirs() }
                sync.from(src)
                sync.into(target)
            }

            settings.syncDirections.forEach {
                registerTasksForSyncDirection(it, project, copyMpsDependencies)
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
            is LocalSource -> registerTasksForLocalSource(syncDirection, project, previousTask, jsonDir)
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
        }
        return exportFromModelServer
    }

    private fun registerTasksForLocalSource(
        syncDirection: SyncDirection,
        project: Project,
        previousTask: TaskProvider<*>,
        jsonDir: File,
    ): TaskProvider<*> {
        val localSource = syncDirection.source as LocalSource
        val antScript = jsonDir.resolve("build.xml")
        val generateAntScript = project.tasks.register(
            "${syncDirection.name}GenerateAntScriptForExport",
            GenerateAntScriptForMps::class.java,
        ) {
            it.dependsOn(previousTask)
            it.mpsHomePath.set(localSource.mpsHome?.absolutePath)
            it.mpsHeapSize.set(localSource.mpsHeapSize)
            it.repositoryPath.set(localSource.repositoryDir?.absolutePath)
            it.antScriptFile.set(antScript)
            it.mpsDependenciesPath.set(getDependenciesDir(project).absolutePath)
            it.jsonDirPath.set(jsonDir.absolutePath)
            it.exportFlag.set(true)
            it.includedModules.set(syncDirection.includedModules)
            it.includedModulePrefixes.set(syncDirection.includedModulePrefixes)
        }

        val exportFromMps = project.tasks.register("${syncDirection.name}ExportFromMps", ExportFromMps::class.java) {
            it.dependsOn(generateAntScript)
            it.outputs.cacheIf { false }
            it.workingDir = jsonDir
            it.classpath = project.getAntDependencies()
            it.mainClass.set("org.apache.tools.ant.launch.Launcher")
            it.mpsHome.set(localSource.mpsHome)
            it.antScript.set(antScript)
            it.jsonDir.set(jsonDir)
        }
        return exportFromMps
    }

    private fun Project.getAntDependencies() = configurations.getByName(antDependenciesConfigName)

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
            it.registeredLanguages.set(syncDirection.registeredLanguages)
            val serverTarget = syncDirection.target as ServerTarget

            it.url.set(serverTarget.url)
            it.repositoryId.set(serverTarget.repositoryId)
            it.branchName.set(serverTarget.branchName)
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

        val antScript = jsonDir.resolve("build.xml")

        val antDependencies = project.configurations.create("model-import-ant-dependencies")
        project.dependencies.add(antDependencies.name, "org.apache.ant:ant-junit:1.10.12")

        val generateAntScriptName = "${syncDirection.name}GenerateAntScriptForImport"
        val generateAntScript = project.tasks.register(generateAntScriptName, GenerateAntScriptForMps::class.java) {
            it.dependsOn(previousTask)
            it.mpsHomePath.set(localTarget.mpsHome?.absolutePath)
            it.mpsHeapSize.set(localTarget.mpsHeapSize)
            it.repositoryPath.set(localTarget.repositoryDir?.absolutePath)
            it.antScriptFile.set(antScript)
            it.mpsDependenciesPath.set(getDependenciesDir(project).absolutePath)
            it.jsonDirPath.set(jsonDir.absolutePath)
            it.exportFlag.set(false)
            it.includedModules.set(syncDirection.includedModules)
            it.includedModulePrefixes.set(syncDirection.includedModulePrefixes)
        }

        val importName = "${syncDirection.name}ImportIntoMps"
        val importIntoMps = project.tasks.register(importName, ImportIntoMps::class.java) {
            it.dependsOn(generateAntScript)
            it.workingDir = jsonDir
            it.classpath = project.getAntDependencies()
            it.mainClass.set("org.apache.tools.ant.launch.Launcher")
            it.jsonDir.set(jsonDir)
            it.antScript.set(jsonDir.resolve("build.xml"))
            it.mpsHome.set(localTarget.mpsHome)
        }

        project.tasks.register("runSync${syncDirection.name.replaceFirstChar { it.uppercaseChar() }}") {
            it.dependsOn(importIntoMps)
            it.group = "modelix"
        }
    }

    private fun getBaseDir(project: Project): File {
        return project.layout.buildDirectory.dir("model-sync").get().asFile
    }

    private fun getDependenciesDir(project: Project): File {
        return getBaseDir(project).resolve("dependencies")
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
