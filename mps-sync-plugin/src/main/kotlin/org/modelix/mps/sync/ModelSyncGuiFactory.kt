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
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import jetbrains.mps.ide.project.ProjectHelper
import org.modelix.mps.sync.icons.CloudIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.util.Objects
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class ModelSyncGuiFactory : ToolWindowFactory, Disposable {

    private var log: Logger = logger<ModelSyncGuiFactory>()
    private lateinit var toolWindowContent: ModelSyncGui
    private lateinit var content: Content

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("-------------------------------------------- createToolWindowContent")

        toolWindowContent = ModelSyncGui(toolWindow)
        content = ContentFactory.SERVICE.getInstance().createContent(toolWindowContent.contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun dispose() {
        log.info("-------------------------------------------- disposing ModelSyncGuiFactory")
        content.dispose()
    }

    private class ModelSyncGui(toolWindow: ToolWindow) {

        private var log: Logger = logger<ModelSyncGui>()
        val contentPanel = JPanel()
        private val status = JLabel()
        private val connection = JLabel()
        private val info = JLabel()

        // the actual intelliJ service handling the synchronization
        val modelSyncService = service<ModelSyncService>()
        var openProjectModel: DefaultComboBoxModel<Project> = DefaultComboBoxModel<Project>()

        init {
            log.info("-------------------------------------------- ModelSyncGui init")
            contentPanel.setLayout(BorderLayout(0, 20))
            contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0))
            contentPanel.add(createSynchronizationPanel(), BorderLayout.PAGE_START)
            contentPanel.add(createControlsPanel(toolWindow), BorderLayout.PAGE_END)
            contentPanel.add(createConnectionPanel(toolWindow), BorderLayout.CENTER)
            toolWindow.setIcon(CloudIcons.ROOT_ICON)
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

        private fun createConnectionPanel(toolWindow: ToolWindow): JPanel {
            val controlsPanel = JPanel()

            val cb: ComboBox<Project> = ComboBox<Project>()
            cb.model = openProjectModel
            cb.renderer = ProjectRenderer()
            controlsPanel.add(cb)

            val connectProjectButton = JButton("Connect")
            connectProjectButton.addActionListener { e: ActionEvent? ->
                modelSyncService.bindProject(ProjectHelper.fromIdeaProject(openProjectModel.selectedItem as Project)!!)
            }
            controlsPanel.add(connectProjectButton)

            val disConnectProjectButton = JButton("DisConnect")
            disConnectProjectButton.addActionListener { e: ActionEvent? ->
                modelSyncService.unbindProject(ProjectHelper.fromIdeaProject(openProjectModel.selectedItem as Project)!!)
            }
            controlsPanel.add(disConnectProjectButton)

            return controlsPanel
        }

        private fun triggerRefresh() {
            populateCB()
        }

        fun populateCB() {
            openProjectModel.removeAllElements()
            openProjectModel.addAll(ProjectManager.getInstance().openProjects.toMutableList())
        }
    }
}

class ProjectRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        var item = value ?: return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        // if the item to be rendered is Project then display the name only
        if (item is Project) {
            item = (item as Project).name
        }
        return super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
    }
}
