/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.plugin.configuration.CloudResourcesConfigurationComponent

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class MPSSynchronizationAppLifecycleListener : AppLifecycleListener {

    private val logger = logger<MPSSynchronizationAppLifecycleListener>()

//    not supported in MPS >= 2022.3
//    override fun appStarting(projectFromCommandLine: Project?) {
//        service<ModelSyncService>().ensureStarted()
//    }

    private var persist: CloudResourcesConfigurationComponent? = null

    override fun appStarting(project: Project?) {
    }

    override fun appStarted() {
        logger.info("============================================ app started")
        service<ModelSyncService>().ensureStarted()

        // this is just a dummy call to instantiate the CloudResourcesConfigurationComponent that enables state persistence...
        val project = ProjectManager.getInstance().openProjects.first()
        persist = project.service<CloudResourcesConfigurationComponent>()
        val state = persist?.state
        logger.info("============================================  is persist null? ${persist == null}")
        logger.info("============================================  THAT'S THE STATE")
        logger.info(state?.toString())
    }

    override fun appClosing() {
        logger.info("============================================  State modification count = ${persist?.stateModificationCount}")
    }
}
