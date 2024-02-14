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

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.reflect.KProperty

/**
 * This component handles the storage of the cloud configuration.
 * For information about component persistence refer to https://jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    // TODO see what happens when we switch between projects. If the state gets persisted and/or overriden by the info from another project.
    // storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    // storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
)
class CloudResourcesConfigurationComponent :
    SimplePersistentStateComponent<CloudResourcesConfigurationComponent.State>(State.INSTANCE) {

    private val logger = logger<CloudResourcesConfigurationComponent>()

    override fun loadState(state: State) {
        super.loadState(state)
        logger.info("State is loaded!!")
        logger.info(state.toString())
    }

    class State : BaseState() {

        @Transient
        private val logger = logger<State>()

        var aaa by string("this is it")

        @OptionTag(converter = SetConverter::class)
        var modelServers: MutableSet<String> = mutableSetOf()

        @OptionTag(converter = SetConverter::class)
        var transientProjects: MutableSet<String> = mutableSetOf()

        /*@get:OptionTag(converter = SetConverter::class)
        var modelServers by HashSetDelegate()

        @get:OptionTag(converter = SetConverter::class)
        var transientProjects by HashSetDelegate()*/

        companion object {
            val INSTANCE = State()
        }

        fun addSomeData() {
            aaa = "this is that"

            modelServers.add("Hello")
            this.incrementModificationCount()
            modelServers.add("World")
            this.incrementModificationCount()
            transientProjects.add("42")
            this.incrementModificationCount()

            logger.info("Some data is added")
        }

        override fun toString(): String {
            return "State(cloudRepositories: $modelServers, transientProjects: $transientProjects)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false

            other as State

            if (aaa != other.aaa) return false
            if (modelServers != other.modelServers) return false
            if (transientProjects != other.transientProjects) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + aaa.hashCode()
            result = 31 * result + modelServers.hashCode()
            result = 31 * result + transientProjects.hashCode()
            return result
        }
    }

    class HashSetDelegate {
        private var storedValue = HashSet<String>()

        private val logger = logger<HashSetDelegate>()

        operator fun getValue(thisRef: Any?, property: KProperty<*>): HashSet<String> {
            logger.info("HashSetDelegate.get() is called. Field contains: $storedValue")
            return storedValue
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: HashSet<String>) {
            logger.info("HashSetDelegate.set() is called. Field contains: $value")
            storedValue = value
        }
    }

    internal class SetConverter : Converter<HashSet<String>?>() {

        private val logger = logger<SetConverter>()

        override fun fromString(value: String): HashSet<String> {
            logger.info("SetConverter.fromString() $value")
            return value.split(",").toHashSet()
        }

        override fun toString(value: HashSet<String>): String {
            logger.info("SetConverter.toString() $value")

            return value.joinToString(",")
        }
    }
}
