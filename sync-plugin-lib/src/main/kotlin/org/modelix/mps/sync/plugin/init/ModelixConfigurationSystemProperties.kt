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

package org.modelix.mps.sync.plugin.init

// status: ready to test
object ModelixConfigurationSystemProperties {

    private val logger = mu.KotlinLogging.logger {}

    val EXECUTION_MODE_SYSPROP = "modelix.executionMode"
    private val LOAD_PERSISTENT_BINDIND_SYSPROP = "modelix.loadPersistentBinding"
    private val EXPORT_PATH_SYSPROP = ModelixExportConfiguration.PATH
    val CLOUD_REPOS_SYSPROP = "modelix.cloud.repos"

    fun shouldLoadPersistentBinding(): Boolean {
        val flagString = PropertyOrEnv[LOAD_PERSISTENT_BINDIND_SYSPROP]
        if (flagString?.isEmpty() == true) {
            return true
        }
        return flagString.toBoolean()
    }

    fun getExecutionMode(): EModelixExecutionMode {
        val executionModeString = PropertyOrEnv[EXECUTION_MODE_SYSPROP]
        var executionMode = EModelixExecutionMode.DEFAULT
        if (executionModeString?.isEmpty() == true) {
            val value = PropertyOrEnv["disable.autobinding"]
            if (value != null && (value.toLowerCase() == "true")) {
                executionMode = EModelixExecutionMode.INTEGRATION_TESTS
            }

            if (PropertyOrEnv[EXPORT_PATH_SYSPROP]?.isNotEmpty() == true) {
                executionMode = EModelixExecutionMode.MODEL_EXPORT
            }
        } else {
            try {
                if (executionModeString == null) {
                    throw IllegalArgumentException()
                }
                executionMode = EModelixExecutionMode.valueOf(executionModeString)
            } catch (ex: IllegalArgumentException) {
                logger.error(ex) { "Unknown execution mode: $executionModeString" }
            }
        }
        System.setProperty(EXECUTION_MODE_SYSPROP, executionMode.name)
        return executionMode
    }
}
