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
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * This component handles the storage of the cloud configuration.
 * For information about component persistence refer to https://jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    reloadable = true,
    storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
)
class CloudResourcesConfigurationComponent : PersistentStateComponent<CloudResourcesConfigurationComponent.State> {

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    class State {
        val modelServers = mutableSetOf<String>()
        val transientProjects = mutableSetOf<String>()
        val transientModules = mutableSetOf<String>()
        val mappedModules = mutableSetOf<String>()

        override fun hashCode(): Int {
            var hc = 1
            hc += 3 * modelServers.hashCode()
            hc += 7 * transientProjects.hashCode()
            hc += 11 * transientModules.hashCode()
            hc += 13 * mappedModules.hashCode()
            return hc
        }

        @Override
        override fun equals(other: Any?): Boolean {
            if (other is State) {
                if (transientProjects != other.transientProjects) {
                    return false
                }
                if (modelServers != other.modelServers) {
                    return false
                }
                if (transientModules != other.transientModules) {
                    return false
                }
                if (mappedModules != other.mappedModules) {
                    return false
                }
                return true
            } else {
                return false
            }
        }

        override fun toString(): String {
            return "State(cloudRepositories: $modelServers, transientProjects: $transientProjects, transientModules: $transientModules, mappedModules: $mappedModules)"
        }
    }
}
