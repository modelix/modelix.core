/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.mps

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object ActiveMpsProjectInjector {

    // TODO what shall happen if we switch MPSProjects? Some threads might still be working on the old MPSProject. (with other words: search for all places where this field is referred to and think about if it can cause trouble if we change this reference to another one suddenly..)
    var activeMpsProject: MPSProject? = null
        private set

    private var activeIdeaProject: Project? = null

    fun setActiveProject(project: Project) {
        if (activeIdeaProject != project) {
            activeIdeaProject = project
            subscribeForApplicationClosing(activeIdeaProject!!)
            activeMpsProject = ProjectHelper.fromIdeaProject(activeIdeaProject)
        }
    }

    private fun subscribeForApplicationClosing(project: Project) {
        /*
         * Subscribe for application closing event and do not delete the modules and models in that case.
         * Explanation: when closing MPS, MPS unregisters all modules from the repository then it calls the
         * moduleRemoved and modelRemoved methods after the module was unregistered. At that point of time,
         * it might happen that the binding is still living, but we do not want to remove the module/model from
         * the server.
         */
        project.messageBus.connect().subscribe(
            AppLifecycleListener.TOPIC,
            object : AppLifecycleListener {
                override fun appWillBeClosed(isRestart: Boolean) {
                    ApplicationLifecycleTracker.applicationClosing = true
                }
            },
        )
    }
}
