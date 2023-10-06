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

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class ProjectSettingsComponent {
    // TODO: decide the scope of the plugin. do we need extra settings for users? if so, this is the way to go

    val panel: JPanel
    private val addServerInput = JBTextField()
    private val statusCheckBox = JBCheckBox("Status ")

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Add Server URL: "), addServerInput, 1, false)
            .addComponent(statusCheckBox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = addServerInput

    var addServerText: String
        get() = addServerInput.text
        set(newText) { addServerInput.text = newText }

    var status: Boolean
        get() = statusCheckBox.isSelected
        set(newStatus) { statusCheckBox.setSelected(newStatus) }
}
