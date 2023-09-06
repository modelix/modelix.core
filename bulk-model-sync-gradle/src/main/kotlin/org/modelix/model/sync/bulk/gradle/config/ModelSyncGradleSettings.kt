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

package org.modelix.model.sync.bulk.gradle.config

import org.gradle.api.Action
import org.modelix.model.api.ILanguage
import org.modelix.model.mpsadapters.RepositoryLanguage
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
    internal var source: SyncEndPoint? = null,
    internal var target: SyncEndPoint? = null,
    internal val includedModules: Set<String> = mutableSetOf(),
    internal val registeredLanguages: Set<ILanguage> = mutableSetOf(RepositoryLanguage),
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

    fun registerLanguage(language: ILanguage) {
        (registeredLanguages as MutableSet).add(language)
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
