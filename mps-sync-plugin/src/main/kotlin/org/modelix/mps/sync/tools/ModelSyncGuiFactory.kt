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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.BindingImpl
import org.modelix.mps.sync.ModelSyncService
import org.modelix.mps.sync.binding.IBinding
import org.modelix.mps.sync.icons.CloudIcons
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator

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
        var serverURL: JBTextField = JBTextField(20)
        var repositoryName: JBTextField = JBTextField(20)
        var branchName: JBTextField = JBTextField(20)
        var modelName: JBTextField = JBTextField(20)
        var jwt: JBTextField = JBTextField(20)

        var openProjectModel: DefaultComboBoxModel<Project> = DefaultComboBoxModel<Project>()
        var existingConnectionsModel: DefaultComboBoxModel<ModelClientV2> = DefaultComboBoxModel<ModelClientV2>()
        var existingBindingModel: DefaultComboBoxModel<IBinding> = DefaultComboBoxModel<IBinding>()
        var repoModel: DefaultComboBoxModel<RepositoryId> = DefaultComboBoxModel<RepositoryId>()
        var branchModel: DefaultComboBoxModel<BranchReference> = DefaultComboBoxModel<BranchReference>()
        var modelModel: DefaultComboBoxModel<INode> = DefaultComboBoxModel<INode>()

        init {
            log.info("-------------------------------------------- ModelSyncGui init")
            toolWindow.setIcon(CloudIcons.ROOT_ICON)
            contentPanel.setLayout(FlowLayout())
            contentPanel.add(getInputBox(toolWindow))
            triggerRefresh()
        }

        private fun getInputBox(toolWindow: ToolWindow): Box {
            // todo: yes i know this is bad code. its for debugging only...

            val inputBox = Box.createVerticalBox()

            val urlPanel = JPanel()
            urlPanel.add(JLabel("Server URL:    "))
            urlPanel.add(serverURL)

            val refreshButton = JButton("Refresh All")
            refreshButton.addActionListener { triggerRefresh() }
            urlPanel.add(refreshButton)
            inputBox.add(urlPanel)

            val jwtPanel = JPanel()
            jwtPanel.add(JLabel("JWT:           "))
            jwtPanel.add(jwt)

            val connectProjectButton = JButton("Connect")
            connectProjectButton.addActionListener { _: ActionEvent ->
                modelSyncService.connectModelServer(
                    serverURL.text,
                    jwt.text,
                    ::triggerRefresh,
                )
            }
            jwtPanel.add(connectProjectButton)
            inputBox.add(jwtPanel)

            inputBox.add(JSeparator())

            val connectionsPanel = JPanel()
            val existingConnectionsCB: ComboBox<ModelClientV2> = ComboBox<ModelClientV2>()
            existingConnectionsCB.model = existingConnectionsModel
            existingConnectionsCB.renderer = CustomCellRenderer()
            connectionsPanel.add(JLabel("Existing Conn.:"))
            connectionsPanel.add(existingConnectionsCB)

            val disConnectProjectButton = JButton("Disconnect")
            disConnectProjectButton.addActionListener { _: ActionEvent? ->
                if (existingConnectionsModel.size > 0) {
                    modelSyncService.disconnectServer(
                        existingConnectionsModel.selectedItem as ModelClientV2,
                        ::triggerRefresh,
                    )
                }
            }
            connectionsPanel.add(disConnectProjectButton)
            inputBox.add(connectionsPanel)

            inputBox.add(JSeparator())

            val targetPanel = JPanel()
            val projectCB: ComboBox<Project> = ComboBox<Project>()
            projectCB.model = openProjectModel
            projectCB.renderer = CustomCellRenderer()
            targetPanel.add(JLabel("Target Project:"))
            targetPanel.add(projectCB)
            inputBox.add(targetPanel)

            val repoPanel = JPanel()
            val repoCB: ComboBox<RepositoryId> = ComboBox<RepositoryId>()
            repoCB.model = repoModel
            repoCB.renderer = CustomCellRenderer()
            repoPanel.add(JLabel("Remote Repo:   "))
            repoPanel.add(repoCB)
            inputBox.add(repoPanel)

            val branchPanel = JPanel()
            val branchCB: ComboBox<BranchReference> = ComboBox<BranchReference>()
            branchCB.model = branchModel
            branchCB.renderer = CustomCellRenderer()
            branchPanel.add(JLabel("Remote Branch: "))
            branchPanel.add(branchCB)
            inputBox.add(branchPanel)

            val modelPanel = JPanel()
            val modelCB: ComboBox<INode> = ComboBox<INode>()
            modelCB.model = modelModel
            modelCB.renderer = CustomCellRenderer()
            modelPanel.add(JLabel("Remote Model:  "))
            modelPanel.add(modelCB)

            val bindButton = JButton("Bind Selected")
            bindButton.addActionListener { _: ActionEvent? ->
                if (existingConnectionsModel.size > 0) {
                    modelSyncService.bindProject(
                        existingConnectionsModel.selectedItem as ModelClientV2,
                        ProjectHelper.fromIdeaProject(openProjectModel.selectedItem as Project)!!,
                        branchName.text,
                        modelName.text,
                        repositoryName.text,
                        ::triggerRefresh,
                    )
                }
            }
            modelPanel.add(bindButton)
            inputBox.add(modelPanel)

            inputBox.add(JSeparator())

            val bindingsPanel = JPanel()
            val existingBindingCB: ComboBox<IBinding> = ComboBox<IBinding>()
            existingBindingCB.model = existingBindingModel
            existingBindingCB.renderer = CustomCellRenderer()
            bindingsPanel.add(JLabel("Bindings:      "))
            bindingsPanel.add(existingBindingCB)

            val unbindButton = JButton("Unbind Selected")
            unbindButton.addActionListener { _: ActionEvent? ->
                // todo
            }
            bindingsPanel.add(unbindButton)
            inputBox.add(bindingsPanel)

            return inputBox
        }

        private fun afterBind() {
            log.info("-------------------------------------------- ModelSyncGui afterBind")
            populateBindingCB()
        }

        private fun triggerRefresh() {
            populateProjectsCB()
            populateConnectionsCB()
            populateRepoCB()
            populateBindingCB()

            // TODO fixme hardcoded values!
            serverURL.text = "http://127.0.0.1:28101/v2"
            repositoryName.text = "courses"
            branchName.text = "master"
            modelName.text = "University.Schedule.modelserver.backend.sandbox"
            jwt.text = ""
        }

        fun populateProjectsCB() {
            openProjectModel.removeAllElements()
            openProjectModel.addAll(ProjectManager.getInstance().openProjects.toMutableList())
            if (openProjectModel.size > 0) {
                openProjectModel.selectedItem = openProjectModel.getElementAt(0)
            }
        }

        fun populateConnectionsCB() {
            existingConnectionsModel.removeAllElements()
            existingConnectionsModel.addAll(modelSyncService.syncService.clientBindingMap.keys)
            if (existingConnectionsModel.size > 0) {
                existingConnectionsModel.selectedItem = existingConnectionsModel.getElementAt(0)
            }
        }

        fun populateRepoCB() {
            repoModel.removeAllElements()
            if (existingConnectionsModel.size != 0) {
                val item = existingConnectionsModel.selectedItem as ModelClientV2
                CoroutineScope(Dispatchers.Default).launch {
                    repoModel.addAll(item.listRepositories())
                    if (repoModel.size > 0) {
                        repoModel.selectedItem = repoModel.getElementAt(0)
                        populateBranchCB()
                    }
                }
            }
        }

        fun populateBranchCB() {
            branchModel.removeAllElements()
            if (existingConnectionsModel.size != 0 && repoModel.size != 0) {
                CoroutineScope(Dispatchers.Default).launch {
                    branchModel.addAll((existingConnectionsModel.selectedItem as ModelClientV2).listBranches(repoModel.selectedItem as RepositoryId))
                    if (branchModel.size > 0) {
                        branchModel.selectedItem = branchModel.getElementAt(0)
                        populateModelCB()
                    }
                }
            }
        }

        fun populateModelCB() {
            modelModel.removeAllElements()
            if (existingConnectionsModel.size != 0 && repoModel.size != 0 && branchModel.size != 0) {
                CoroutineScope(Dispatchers.Default).launch {
                    val qq: IBranch = (existingConnectionsModel.selectedItem as ModelClientV2).getReplicatedModel(branchModel.selectedItem as BranchReference).start()
                    qq.runRead {
                        val aa: Iterable<INode> = qq.getRootNode().allChildren
                        modelModel.addAll(aa.toList())
                    }
                    if (modelModel.size > 0) {
                        modelModel.selectedItem = modelModel.getElementAt(0)
                    }
                }
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

    class CustomCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            var item = value ?: return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            when (item) {
                is Project -> item = item.name
                is BindingImpl -> item = "Repo: ? | Branch: ${item.replicatedModel.branchRef}"
                is ModelClientV2 -> item = item.baseUrl
                is RepositoryId -> item = item.toString()
                is BranchReference -> item = item.branchName
                is INode -> item = "$item" // (${item.concept.toString()})
            }
            return super.getListCellRendererComponent(list, item, index, isSelected, cellHasFocus)
        }
    }
}
