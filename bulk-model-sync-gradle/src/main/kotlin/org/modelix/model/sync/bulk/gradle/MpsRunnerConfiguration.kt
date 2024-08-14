package org.modelix.model.sync.bulk.gradle

import org.modelix.buildtools.runner.BundledPluginPath
import org.modelix.buildtools.runner.ExternalPluginPath
import org.modelix.buildtools.runner.MPSRunnerConfig
import org.modelix.buildtools.runner.PluginConfig
import org.modelix.model.sync.bulk.gradle.config.BundledPluginSpec
import org.modelix.model.sync.bulk.gradle.config.ExternalPluginSpec
import org.modelix.model.sync.bulk.gradle.config.LocalSource
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.PluginSpec
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import java.io.File

internal fun buildMpsRunConfigurationForLocalSources(
    syncDirection: SyncDirection,
    classPathElements: Set<File>,
    jsonDir: File,
): MPSRunnerConfig {
    val source = requireNotNull(syncDirection.source)
    val localSource = source as? LocalSource
        ?: throw IllegalArgumentException("`syncDirection.source` is ${source::class.java} but should be ${LocalTarget::class.java}.")
    val config = MPSRunnerConfig(
        mainClassName = "org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer",
        mainMethodName = "exportRepository",
        classPathElements = classPathElements.toList(),
        mpsHome = localSource.mpsHome,
        workDir = jsonDir,
        additionalModuleDirs = localSource.mpsLibraries.toList() + listOfNotNull(localSource.repositoryDir),
        plugins = createPluginConfig(localSource.mpsPlugins),
        jvmArgs = listOfNotNull(
            "-Dmodelix.mps.model.sync.bulk.output.path=${jsonDir.absolutePath}",
            "-Dmodelix.mps.model.sync.bulk.output.modules=${syncDirection.includedModules.joinToString(",")}",
            "-Dmodelix.mps.model.sync.bulk.output.modules.prefixes=${
                syncDirection.includedModulePrefixes.joinToString(
                    ",",
                )
            }",
            "-Dmodelix.mps.model.sync.bulk.repo.path=${localSource.repositoryDir?.absolutePath}",
            "-Xmx${localSource.mpsHeapSize}",
            localSource.mpsDebugPort?.let { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$it" },
        ),
    )
    return config
}

internal fun buildMpsRunConfigurationForLocalTarget(
    syncDirection: SyncDirection,
    classPathElements: Set<File>,
    jsonDir: File,
): MPSRunnerConfig {
    val target = requireNotNull(syncDirection.target)
    val localTarget = target as? LocalTarget
        ?: throw IllegalArgumentException("`syncDirection.target` is ${target::class.java} but should be ${LocalTarget::class.java}.")
    val repositoryDir = checkNotNull(localTarget.repositoryDir) {
        "syncDirection.target has no `repositoryDir` specified."
    }
    val hasBaseRevision = (syncDirection.source as? ServerSource)?.baseRevision != null
    val config = MPSRunnerConfig(
        mainClassName = "org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer",
        mainMethodName = if (hasBaseRevision) "importRepositoryFromModelServer" else "importRepository",
        classPathElements = classPathElements.toList(),
        mpsHome = localTarget.mpsHome,
        workDir = jsonDir,
        additionalModuleDirs = localTarget.mpsLibraries.toList() + repositoryDir,
        plugins = createPluginConfig(localTarget.mpsPlugins),
        jvmArgs = listOfNotNull(
            "-Dmodelix.mps.model.sync.bulk.input.path=${jsonDir.absolutePath}",
            "-Dmodelix.mps.model.sync.bulk.input.modules=${syncDirection.includedModules.joinToString(",")}",
            "-Dmodelix.mps.model.sync.bulk.input.modules.prefixes=${syncDirection.includedModulePrefixes.joinToString(",")}",
            "-Dmodelix.mps.model.sync.bulk.repo.path=${repositoryDir.absolutePath}",
            "-Dmodelix.mps.model.sync.bulk.input.continueOnError=${syncDirection.continueOnError}",
            "-Dmodelix.mps.model.sync.bulk.server.repository=${(syncDirection.source as ServerSource).repositoryId}".takeIf { hasBaseRevision },
            "-Dmodelix.mps.model.sync.bulk.server.url=${(syncDirection.source as ServerSource).url}".takeIf { hasBaseRevision },
            "-Dmodelix.mps.model.sync.bulk.server.version.hash=${(syncDirection.source as ServerSource).revision}".takeIf { hasBaseRevision },
            "-Dmodelix.mps.model.sync.bulk.server.version.base.hash=${(syncDirection.source as ServerSource).baseRevision}".takeIf { hasBaseRevision },
            "-Xmx${localTarget.mpsHeapSize}",
            localTarget.mpsDebugPort?.let { "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$it" },
        ),
    )
    return config
}

private fun createPluginConfig(mpsPlugins: Set<PluginSpec>): List<PluginConfig> {
    return mpsPlugins.map {
        val pluginPath = when (it) {
            is BundledPluginSpec -> BundledPluginPath(it.folder)
            is ExternalPluginSpec -> ExternalPluginPath(it.folder)
        }
        PluginConfig(it.id, pluginPath)
    }
}
