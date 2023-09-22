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
object ModelixExportConfiguration {
    private val PREFIX = "modelix.export."
    val PATH = PREFIX + "path"
    val STARTED = PREFIX + "started"
    val DONE = PREFIX + "done"
    val BRANCH_NAME = PREFIX + "branchName"
    val REPOSITORY_ID = PREFIX + "repositoryId"
    val SERVER_URL = PREFIX + "serverUrl"
    val GRADLE_PLUGIN_SOCKET_PORT = PREFIX + "gradlePluginSocketPort"
    val MAKE = PREFIX + "make"
}
