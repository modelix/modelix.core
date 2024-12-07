package org.modelix.model.sync.bulk.gradle.config

import org.gradle.api.Action
import org.modelix.kotlin.utils.DeprecationInfo
import org.modelix.model.api.ILanguage
import java.io.File

open class ModelSyncGradleSettings {
    internal val syncDirections = mutableListOf<SyncDirection>()
    internal val taskDependencies = mutableListOf<Any>()

    fun direction(name: String, action: Action<SyncDirection>) {
        val syncDirection = SyncDirection(name)
        action.execute(syncDirection)
        syncDirections.add(syncDirection)
    }

    fun dependsOn(task: Any) {
        taskDependencies.add(task)
    }
}

data class SyncDirection(
    internal val name: String,
    internal var source: SyncEndpoint? = null,
    internal var target: SyncEndpoint? = null,
    internal val includedModules: Set<String> = mutableSetOf(),
    internal val includedModulePrefixes: Set<String> = mutableSetOf(),
    internal val excludedModules: Set<String> = mutableSetOf(),
    internal val excludedModulePrefixes: Set<String> = mutableSetOf(),
    internal var continueOnError: Boolean = false,
) {
    fun fromModelServer(action: Action<ServerSource>) {
        val endpoint = ServerSource()
        action.execute(endpoint)
        source = endpoint
    }

    fun fromLocal(action: Action<LocalSource>) {
        val endpoint = LocalSource()
        action.execute(endpoint)
        source = endpoint
    }

    fun toModelServer(action: Action<ServerTarget>) {
        val endpoint = ServerTarget()
        action.execute(endpoint)
        target = endpoint
    }

    fun toLocal(action: Action<LocalTarget>) {
        val endpoint = LocalTarget()
        action.execute(endpoint)
        target = endpoint
    }

    fun includeModule(module: String) {
        (includedModules as MutableSet).add(module)
    }

    fun includeModulesByPrefix(prefix: String) {
        (includedModulePrefixes as MutableSet).add(prefix)
    }

    fun excludeModule(module: String) {
        (excludedModules as MutableSet).add(module)
    }

    fun excludeModulesByPrefix(prefix: String) {
        (excludedModulePrefixes as MutableSet).add(prefix)
    }

    @Deprecated("Registering languages is not necessary. This call can be safely removed.", ReplaceWith(""))
    @DeprecationInfo(since = "2024-01-08")
    fun registerLanguage(language: ILanguage) {}

    fun enableContinueOnError(state: Boolean) {
        continueOnError = state
    }
}

interface SyncEndpoint {
    fun getValidationErrors(): List<String>
}

sealed interface LocalEndpoint : SyncEndpoint {
    var mpsHome: File?
    var mpsHeapSize: String
    var repositoryDir: File?
    var mpsDebugPort: Int?

    /**
     * Add a plugin to be loaded when running MPS.
     * `jetbrains.mps.core`, `jetbrains.mps.testing`, `jetbrains.mps.ide.make` are always loaded.
     *
     * All other plugins, even bundled ones, must be configured explicitly.
     * In general cases, the sync does not rely on concepts (and in turn on languages and plugins) of the synced nodes.
     *
     * Loading other plugins might become necessary when they provide custom persistence
     * and in other, yet unknown cases.
     * First, try if the sync works for your project without adding plugins.
     *
     * Example usage:
     * ```
     * mpsPlugin(BundledPluginSpec("jetbrains.mps.vcs", File("plugins/mps-vcs")))
     * mpsPlugin(ExternalPluginSpec("com.example.mps.aPlugin", File("/full/path/to/aPlugin")))
     * ```
     */
    fun mpsPlugin(plugin: PluginSpec)

    fun mpsLibrary(folder: File)

    override fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (repositoryDir == null) {
            errors.addUndefinedLocalFieldError("repositoryDir")
        }
        return errors
    }
}

data class LocalSource(
    override var mpsHome: File? = null,
    internal var mpsLibraries: Set<File> = emptySet(),
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
    override var mpsDebugPort: Int? = null,
    internal var mpsPlugins: Set<PluginSpec> = emptySet(),
) : LocalEndpoint {
    override fun mpsLibrary(folder: File) {
        mpsLibraries += folder
    }
    override fun mpsPlugin(plugin: PluginSpec) {
        mpsPlugins += plugin
    }
}

data class LocalTarget(
    override var mpsHome: File? = null,
    internal var mpsLibraries: Set<File> = emptySet(),
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
    override var mpsDebugPort: Int? = null,
    internal var mpsPlugins: Set<PluginSpec> = emptySet(),
) : LocalEndpoint {
    override fun mpsLibrary(folder: File) {
        mpsLibraries += folder
    }
    override fun mpsPlugin(plugin: PluginSpec) {
        mpsPlugins += plugin
    }
}

/**
 * Specifies an MPS-plugin to be loaded.
 * The [id] is the one that can be found in the `META-INF/plugin.xml` of a plugin.
 */
sealed interface PluginSpec {
    val id: String
}

/**
 * Specifies a plugin by specifying its installation [folder]
 * that will be resolved against the installation of MPS.
 */
data class BundledPluginSpec(override val id: String, val folder: File) : PluginSpec

/**
 * Specifies a plugin by specifying its installation [folder]
 * that can be absolute or will be resolved depending on the JVM process it is executed in.
 */
data class ExternalPluginSpec(override val id: String, val folder: File) : PluginSpec

private const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 5 * 60

sealed interface ServerEndpoint : SyncEndpoint {
    var url: String?
    var repositoryId: String?
    var branchName: String?
    var requestTimeoutSeconds: Int

    override fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (url == null) {
            errors.addUndefinedServerFieldError("url")
        }
        return errors
    }
}

data class ServerSource(
    override var url: String? = null,
    override var repositoryId: String? = null,
    override var branchName: String? = null,
    override var requestTimeoutSeconds: Int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
    var revision: String? = null,
    var baseRevision: String? = null,
) : ServerEndpoint {
    override fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        errors.addAll(super.getValidationErrors())
        if (revision != null) {
            // If a revision is specified, repo and branch are not required
            return errors
        }

        if (repositoryId == null && branchName == null) {
            // Give hint is configuration is completely off
            errors.add("Invalid server source. Please either specify a revision or repositoryId and branchName.")
            return errors
        }

        // Configuration is incomplete
        if (repositoryId == null) {
            errors.addUndefinedServerFieldError("repositoryId")
        } else if (branchName == null) {
            errors.addUndefinedServerFieldError("branchName")
        }

        return errors
    }
}

data class ServerTarget(
    override var url: String? = null,
    override var repositoryId: String? = null,
    override var branchName: String? = null,
    override var requestTimeoutSeconds: Int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
    val metaProperties: MutableMap<String, String> = mutableMapOf(),
) : ServerEndpoint {
    override fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        errors.addAll(super.getValidationErrors())

        if (repositoryId == null) {
            errors.addUndefinedServerFieldError("repositoryId")
        }

        if (branchName == null) {
            errors.addUndefinedServerFieldError("branchName")
        }
        return errors
    }
}

private fun MutableList<String>.addUndefinedLocalFieldError(fieldName: String) {
    addUndefinedFieldError(fieldName, "LocalEndpoint")
}

private fun MutableList<String>.addUndefinedServerFieldError(fieldName: String) {
    addUndefinedFieldError(fieldName, "ServerEndpoint")
}

private fun MutableList<String>.addUndefinedFieldError(fieldName: String, block: String) {
    add("Undefined '$fieldName' in '$block'.")
}
