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

package org.modelix.mps.sync.plugin.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * This component handles the storage of the cloud configuration.
 * For information about component persistence refer to https://jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    // TODO see what happens when we switch between projects. If the state gets persisted and/or overriden by the info from another project.
    storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
    reloadable = true,
)
// todo remove project parameter, if not used and if possible
class CloudResourcesConfigurationComponent(project: Project) :
    PersistentStateComponent<CloudResourcesConfigurationComponent.State> {

    class State {
        // TODO implement actual data
        // MutableCollections do not work!!!

        var modelServers: List<String> = listOf<String>()
        var transientProjects: Set<String> = setOf<String>()

        fun addSomeData() {
            modelServers = modelServers + "X"
            transientProjects = transientProjects + "X"
        }
    }

    private var state: State = State()

    override fun getState(): State {
        return state
    }
    override fun loadState(newState: State) {
         state = newState
    }
}
