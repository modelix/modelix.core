package org.modelix.model.sync.gradle.config

import org.gradle.api.Action
import java.io.File

open class ModelSyncGradleSettings {
    internal val syncDirections = mutableListOf<SyncDirection>()

    fun direction(name: String, action: Action<SyncDirection>) {
        val syncDirection = SyncDirection(name)
        action.execute(syncDirection)
        syncDirections.add(syncDirection)
    }
}

data class SyncDirection(
    internal val name: String,
    internal var source: SyncEndPoint? = null,
    internal var target: SyncEndPoint? = null,
    internal val includedModules: MutableList<String> = mutableListOf(),
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
        includedModules.add(module)
    }
}

interface SyncEndPoint {
    fun getValidationErrors(): String
}

sealed interface LocalEndpoint : SyncEndPoint {
    var mpsHome: File?
    var mpsHeapSize: String
    var repositoryDir: File?

    override fun getValidationErrors(): String {
        return buildString {
            if (mpsHome == null) {
                appendUndefinedLocalFieldError("mpsHome")
            }
            if (repositoryDir == null) {
                appendUndefinedLocalFieldError("repositoryDir")
            }
        }
    }
}

data class LocalSource(
    override var mpsHome: File? = null,
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
) : LocalEndpoint

data class LocalTarget(
    override var mpsHome: File? = null,
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
) : LocalEndpoint

sealed interface ServerEndpoint : SyncEndPoint {
    var url: String?
    var repositoryId: String?
    var branchName: String?

    override fun getValidationErrors(): String {
        return buildString {
            if (url == null) {
                appendUndefinedServerFieldError("url")
            }
        }
    }
}

data class ServerSource(
    override var url: String? = null,
    override var repositoryId: String? = null,
    override var branchName: String? = null,
    var revision: String? = null,
) : ServerEndpoint {
    override fun getValidationErrors(): String {
        return buildString {
            append(super.getValidationErrors())
            if (revision == null) {
                if (repositoryId == null && branchName == null) {
                    appendLine("Invalid server source. Please either specify a revision or repositoryId and branchName.")
                } else if (repositoryId == null) {
                    appendUndefinedServerFieldError("repositoryId")
                } else if (branchName == null) {
                    appendUndefinedServerFieldError("branchName")
                }
            }
        }
    }
}

data class ServerTarget(
    override var url: String? = null,
    override var repositoryId: String? = null,
    override var branchName: String? = null,
) : ServerEndpoint {
    override fun getValidationErrors(): String {
        return buildString {
            append(super.getValidationErrors())

            if (repositoryId == null) {
                appendUndefinedServerFieldError("repositoryId")
            }

            if (branchName == null) {
                appendUndefinedServerFieldError("branchName")
            }
        }
    }
}

private fun StringBuilder.appendUndefinedLocalFieldError(fieldName: String) {
    appendUndefinedFieldError(fieldName, "LocalEndpoint")
}

private fun StringBuilder.appendUndefinedServerFieldError(fieldName: String) {
    appendUndefinedFieldError(fieldName, "ServerEndpoint")
}

private fun StringBuilder.appendUndefinedFieldError(fieldName: String, block: String) {
    appendLine("Undefined '$fieldName' in '$block'.")
}
