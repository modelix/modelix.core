package org.modelix.model.sync.bulk.gradle.tasks

import org.gradle.api.DefaultTask
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

/**
 * Instead of throwing exceptions for single configuration errors,
 * this task collects all configuration errors and puts them into a single exception,
 * so the user can see all steps that must be taken at a glance.
 */
@CacheableTask
abstract class ValidateSyncSettings : DefaultTask() {

    @get:Input
    abstract val settings: Property<ModelSyncGradleSettings>

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

            if (direction.source == null || direction.target == null) {
                appendLine("Both source and target have to be defined.")
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

            val sourceErrors = direction.source?.getValidationErrors() ?: emptyList()
            if (sourceErrors.isNotEmpty()) {
                appendLine()
                appendLine(sourceErrors.joinToString(separator = "\n") { it })
            }

            val targetErrors = direction.target?.getValidationErrors() ?: emptyList()
            if (targetErrors.isNotEmpty()) {
                appendLine(targetErrors.joinToString(separator = "\n") { it })
            }
        }

        if (syncDirectionErrors.isNotEmpty()) {
            errorMsgBuilder.appendLine("Invalid sync direction '${direction.name}':")
            errorMsgBuilder.append(syncDirectionErrors.prependIndent("  "))
        }
    }
}
