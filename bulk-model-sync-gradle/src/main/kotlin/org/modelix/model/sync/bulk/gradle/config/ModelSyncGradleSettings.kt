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
    internal val registeredLanguages: Set<ILanguage> = mutableSetOf(),
    internal val includedModulePrefixes: Set<String> = mutableSetOf(),
    internal var mpsDebugEnabled: Boolean = false,
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

    fun registerLanguage(language: ILanguage) {
        (registeredLanguages as MutableSet).add(language)
    }

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

    override fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (mpsHome == null) {
            errors.addUndefinedLocalFieldError("mpsHome")
        }
        if (repositoryDir == null) {
            errors.addUndefinedLocalFieldError("repositoryDir")
        }
        return errors
    }
}

data class LocalSource(
    override var mpsHome: File? = null,
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
    override var mpsDebugPort: Int? = null,
) : LocalEndpoint

data class LocalTarget(
    override var mpsHome: File? = null,
    override var mpsHeapSize: String = "2g",
    override var repositoryDir: File? = null,
    override var mpsDebugPort: Int? = null,
) : LocalEndpoint

sealed interface ServerEndpoint : SyncEndpoint {
    var url: String?
    var repositoryId: String?
    var branchName: String?

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
    var revision: String? = null,
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
