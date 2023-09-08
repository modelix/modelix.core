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

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.Calendar
import java.util.Objects
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class ModelSyncGuiFactory : ToolWindowFactory, Disposable {

    private var log: Logger = Logger.getInstance(this.javaClass)
    private lateinit var toolWindowContent: ModelSyncGui
    lateinit var content: Content

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("-------------------------------------------- createToolWindowContent")

        toolWindowContent = ModelSyncGui(toolWindow)
        content = ContentFactory.SERVICE.getInstance().createContent(toolWindowContent.contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun dispose() {
        content.dispose()
    }

    private class ModelSyncGui(toolWindow: ToolWindow) {

        private var log: Logger = Logger.getInstance(this.javaClass)
        val contentPanel = JPanel()
        private val status = JLabel()
        private val connection = JLabel()
        private val info = JLabel()

        init {
            log.info("-------------------------------------------- ModelSyncGui init")
            contentPanel.setLayout(BorderLayout(0, 20))
            contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0))
            contentPanel.add(createSynchronizationPanel(), BorderLayout.PAGE_START)
            contentPanel.add(createControlsPanel(toolWindow), BorderLayout.CENTER)
            triggerRefresh()
        }

        companion object {
            private const val SOME_ICON_PATH = "/icon.png"
        }

        private fun createSynchronizationPanel(): JPanel {
            val synchronizationPanel = JPanel()
            setIconLabel(status, SOME_ICON_PATH)
            synchronizationPanel.add(status)
            synchronizationPanel.add(connection)
            synchronizationPanel.add(info)
            return synchronizationPanel
        }

        private fun setIconLabel(label: JLabel, imagePath: String) {
            label.setIcon(ImageIcon(Objects.requireNonNull(javaClass.getResource(imagePath))))
        }

        private fun createControlsPanel(toolWindow: ToolWindow): JPanel {
            val controlsPanel = JPanel()

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener { e: ActionEvent? -> triggerRefresh() }
            controlsPanel.add(refreshButton)

            val hideToolWindowButton = JButton("Hide")
            hideToolWindowButton.addActionListener { e: ActionEvent? -> toolWindow.hide(null) }
            controlsPanel.add(hideToolWindowButton)

            return controlsPanel
        }

        private fun triggerRefresh() {
            // todo
            val calendar = Calendar.getInstance()
            status.setText(getCurrentDate(calendar))
            connection.setText(getTimeZone(calendar))
            info.setText(getCurrentTime(calendar))
        }

        private fun getCurrentDate(calendar: Calendar): String {
            return (
                calendar[Calendar.DAY_OF_MONTH].toString() + "/" +
                    (calendar[Calendar.MONTH] + 1) + "/" +
                    calendar[Calendar.YEAR]
                )
        }
        private fun getTimeZone(calendar: Calendar): String {
            val gmtOffset = calendar[Calendar.ZONE_OFFSET].toLong() // offset from GMT in milliseconds
            val gmtOffsetString = (gmtOffset / 3600000).toString()
            return if (gmtOffset > 0) "GMT + $gmtOffsetString" else "GMT - $gmtOffsetString"
        }
        private fun getCurrentTime(calendar: Calendar): String {
            return getFormattedValue(calendar, Calendar.HOUR_OF_DAY) + ":" + getFormattedValue(
                calendar,
                Calendar.MINUTE,
            )
        }
        private fun getFormattedValue(calendar: Calendar, calendarField: Int): String {
            val value = calendar[calendarField]
            return StringUtils.leftPad(value.toString(), 2, "0")
        }
    }
}
