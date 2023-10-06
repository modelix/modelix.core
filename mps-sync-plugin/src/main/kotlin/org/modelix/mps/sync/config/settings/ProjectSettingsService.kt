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

package org.modelix.mps.sync.config.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@Service(Service.Level.PROJECT)
@State(name = "ModelixProjectSettings", storages = [Storage("modelix.xml")])
class ProjectSettingsService : PersistentStateComponent<ProjectSettingsService?> {
    // TODO: decide the scope of the plugin. do we need extra settings for users? if so, this is the way to go

    // model-server map: name -> URL
    var modelServer: String = ""

    var status = false

    override fun getState(): ProjectSettingsService {
        return this
    }

    override fun loadState(state: ProjectSettingsService) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
