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

package org.modelix.mps.sync

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ModelixSyncPluginInitializer : StartupActivity.Background {
    override fun runActivity(project: Project) {
        service<ModelSyncService>().registerProject(project)
    }
}

// TODO use this implementation after dropping support for older MPS versions
// class ModelixSyncPluginInitializer : com.intellij.openapi.startup.ProjectActivity {
//    override suspend fun execute(project: Project) {
//        service<ModelSyncService>().registerProject(project)
//    }
// }
