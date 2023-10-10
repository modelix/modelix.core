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

package org.modelix.mps.sync.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import jetbrains.mps.ide.project.ProjectHelper
import org.modelix.mps.sync.ModelSyncService
import org.modelix.mps.sync.binding.IBinding
import org.modelix.mps.sync.icons.CloudIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
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
        private val iconLabel = JLabel()

        // the actual intelliJ service handling the synchronization
        val modelSyncService = service<ModelSyncService>()
        var serverURL: JBTextField = JBTextField()
        var repositoryName: JBTextField = JBTextField()
        var branchName: JBTextField = JBTextField()
        var modelName: JBTextField = JBTextField()
        var jwt: JBTextField = JBTextField()

        var openProjectModel: DefaultComboBoxModel<Project> = DefaultComboBoxModel<Project>()
        var existingBindingModel: DefaultComboBoxModel<IBinding> = DefaultComboBoxModel<IBinding>()

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

        private fun createSynchronizationPanel(): JPanel {
            val synchronizationPanel = JPanel()
            iconLabel.icon = CloudIcons.PLUGIN_ICON
            synchronizationPanel.add(iconLabel)
            return synchronizationPanel
        }

        private fun createControlsPanel(toolWindow: ToolWindow): JPanel {
            val controlsPanel = JPanel()

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener { triggerRefresh() }
            controlsPanel.add(refreshButton)

            val hideToolWindowButton = JButton("Hide")
            hideToolWindowButton.addActionListener { toolWindow.hide(null) }
            controlsPanel.add(hideToolWindowButton)

            return controlsPanel
        }

        private fun createConnectionPanel(toolWindow: ToolWindow): JPanel {
            val controlsPanel = JPanel()

            controlsPanel.add(serverURL)
            controlsPanel.add(repositoryName)
            controlsPanel.add(branchName)
            controlsPanel.add(modelName)
            controlsPanel.add(jwt)

            val projectCB: ComboBox<Project> = ComboBox<Project>()
            projectCB.model = openProjectModel
            projectCB.renderer = ProjectRenderer()
            controlsPanel.add(projectCB)

            val connectProjectButton = JButton("Connect")
            connectProjectButton.addActionListener { _: ActionEvent ->
                modelSyncService.bindProject(
                    ProjectHelper.fromIdeaProject(openProjectModel.selectedItem as Project)!!,
                    serverURL.text,
                    repositoryName.text,
                    branchName.text,
                    modelName.text,
                    jwt.text,
                    { afterBind() },
                )
            }
            controlsPanel.add(connectProjectButton)

            val existingBindingCB: ComboBox<IBinding> = ComboBox<IBinding>()
            existingBindingCB.model = existingBindingModel
            existingBindingCB.renderer = ProjectRenderer()
            controlsPanel.add(existingBindingCB)

            val disConnectProjectButton = JButton("Disconnect")
            disConnectProjectButton.addActionListener { _: ActionEvent? ->
                if (existingBindingModel.size > 0) {
                    modelSyncService.deactivateBinding(existingBindingModel.selectedItem as IBinding)
                    populateBindingCB()
                }
            }
            controlsPanel.add(disConnectProjectButton)

            return controlsPanel
        }

        private fun afterBind() {
            log.info("-------------------------------------------- ModelSyncGui afterBind")
            populateBindingCB()
        }

        private fun triggerRefresh() {
            populateCB()
            populateBindingCB()
            serverURL.text = "http://127.0.0.1:28101/v2"
            repositoryName.text = "courses"
            branchName.text = "master"
            modelName.text = "modelname"
            jwt.text = ""
        }

        fun populateCB() {
            openProjectModel.removeAllElements()
            openProjectModel.addAll(ProjectManager.getInstance().openProjects.toMutableList())
            if (openProjectModel.size > 0) {
                openProjectModel.selectedItem = openProjectModel.getElementAt(0)
            }
        }

        fun populateBindingCB() {
            existingBindingModel.removeAllElements()
            existingBindingModel.addAll(modelSyncService.getBindingList())
            if (existingBindingModel.size > 0) {
                existingBindingModel.selectedItem = existingBindingModel.getElementAt(0)
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
                item = item.name
            } else if (item is IBinding) {
                item = "${item.javaClass.name} ..."
            }
            return super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
        }
    }
}
