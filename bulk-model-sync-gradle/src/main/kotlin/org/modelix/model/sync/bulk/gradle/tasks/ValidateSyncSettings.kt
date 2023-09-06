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

package org.modelix.model.sync.bulk.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.modelix.model.sync.bulk.gradle.config.LocalSource
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.ModelSyncGradleSettings
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.ServerTarget
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import javax.inject.Inject

@CacheableTask
abstract class ValidateSyncSettings @Inject constructor(of: ObjectFactory) : DefaultTask() {

    @Input
    val settings: Property<ModelSyncGradleSettings> = of.property(ModelSyncGradleSettings::class.java)

    private val errorMsgBuilder = StringBuilder()

    @TaskAction
    fun validate() {
        errorMsgBuilder.clear()
        val settings = settings.get()

        check(settings.syncDirections.isNotEmpty()) { "No sync direction defined. Add one via direction {}." }
        val duplicateNames = settings.syncDirections.groupBy { it.name }
            .filter { (_, list) -> list.size > 1 }
            .map { it.key }
            .toSet()

        if (duplicateNames.isNotEmpty()) {
            error("Duplicate sync directions: ${duplicateNames.joinToString(separator = ", ", prefix = "'", postfix = "'")}")
        }

        settings.syncDirections.forEach { validateSyncDirection(it) }

        val errorMsg = errorMsgBuilder.toString()
        if (errorMsg.isNotEmpty()) {
            error("Invalid Model Sync Settings \n${errorMsg.prependIndent("  ")}")
        }
    }

    private fun validateSyncDirection(direction: SyncDirection) {
        val syncDirectionErrors = buildString {
            if (direction.name.isEmpty()) {
                appendLine("Undefined name.")
            }

            if (direction.source == null) {
                appendLine("Undefined source.")
            }
            if (direction.target == null) {
                appendLine("Undefined target.")
            }

            if (direction.source == null || direction.target == null) {
                return@buildString
            }

            if (direction.source is LocalSource && direction.target !is ServerTarget) {
                appendLine("Invalid target for local source. Currently only server targets are allowed for local sources.")
                return@buildString
            }

            if (direction.source is ServerSource && direction.target !is LocalTarget) {
                appendLine("Invalid target for server source. Currently only local targets are allowed for server sources.")
                return@buildString
            }

            val sourceErrors = direction.source?.getValidationErrors() ?: ""
            if (sourceErrors.isNotEmpty()) {
                appendLine()
                appendLine(sourceErrors)
            }

            val targetErrors = direction.target?.getValidationErrors() ?: ""
            if (targetErrors.isNotEmpty()) {
                appendLine(targetErrors)
            }
        }

        if (syncDirectionErrors.isNotEmpty()) {
            errorMsgBuilder.appendLine("Invalid sync direction '${direction.name}':")
            errorMsgBuilder.append(syncDirectionErrors.prependIndent("  "))
        }
    }
}
