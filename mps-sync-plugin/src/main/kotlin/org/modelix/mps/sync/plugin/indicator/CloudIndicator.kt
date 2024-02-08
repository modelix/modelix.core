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

package org.modelix.mps.sync.plugin.indicator

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.icons.CloudIcons
import java.awt.event.MouseEvent
import javax.swing.JComponent

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class CloudIndicator : StatusBarWidgetFactory {
    companion object {
        private const val ID = "CloudStatus"
        private const val TOOLTIP_CONTENT = "<table><tr><td>{0}:</td><td align=right>{1}</td></tr></table>"
    }
    override fun getId(): String = ID

    @Nls
    override fun getDisplayName(): String = "Cloud Status"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CloudToolWidget()
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
    override fun disposeWidget(widget: StatusBarWidget) {}

    private class CloudToolWidget : JBLabel(), CustomStatusBarWidget {

        init {
            // todo: add listener on model sync service to get connection status automatically
            // service<ModelSyncService>() ...

            object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    // todo: maybe even allow opening the tool bar from here
                    // ToolWindowManager.getInstance(e.project!!).getToolWindow("Modelix Model Synchronization")!!.show()
                    toggleStatus()
                    return true
                }
            }.installOn(this, true)
        }

        override fun ID(): String = ID
        var state = true

        override fun install(statusBar: StatusBar) {
            toggleStatus()
        }

        fun toggleStatus() {
            state = !state
            if (state) {
                this.icon = CloudIcons.CONNECTION_ON
                toolTipText = UIBundle.message(TOOLTIP_CONTENT, TOOLTIP_CONTENT.format("Server 1", "ON"))
            } else {
                this.icon = CloudIcons.CONNECTION_OFF
                toolTipText = UIBundle.message(TOOLTIP_CONTENT, "Server 1", "OFF")
            }
        }
        override fun getComponent(): JComponent = this

        override fun dispose() {}
    }
}
