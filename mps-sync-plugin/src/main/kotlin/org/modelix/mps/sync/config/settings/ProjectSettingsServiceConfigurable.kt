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

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class ProjectSettingsServiceConfigurable(private var project: Project) : Configurable {
    // TODO: decide the scope of the plugin. do we need extra settings for users? if so, this is the way to go

    private var settingsComponent: ProjectSettingsComponent? = null

    override fun createComponent(): JComponent {
        settingsComponent = ProjectSettingsComponent()
        return settingsComponent!!.panel
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Modelix Cloud Settings"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return settingsComponent!!.preferredFocusedComponent
    }

    override fun isModified(): Boolean {
        val settingsService: ProjectSettingsService = project.service<ProjectSettingsService>()
        return (settingsComponent!!.addServerText != settingsService.modelServer) or (settingsComponent!!.status !== settingsService.status)
    }

    override fun apply() {
        val settings: ProjectSettingsService = project.service<ProjectSettingsService>()
        settings.modelServer = settingsComponent!!.addServerText
        settings.status = settingsComponent!!.status
    }

    override fun reset() {
        val settings: ProjectSettingsService = project.service<ProjectSettingsService>()
        settingsComponent!!.addServerText = settings.modelServer
        settingsComponent!!.status = settings.status
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
