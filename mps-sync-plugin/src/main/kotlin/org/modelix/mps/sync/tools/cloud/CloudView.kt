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

package org.modelix.mps.sync.tools.cloud

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.ide.ui.tree.MPSTree
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import jetbrains.mps.ide.ui.tree.TextTreeNode
import jetbrains.mps.workbench.action.ActionUtils
import org.modelix.mps.sync.tools.cloud.tree.CloudBindingTreeNode
import org.modelix.mps.sync.tools.cloud.tree.CloudBranchTreeNode
import org.modelix.mps.sync.tools.cloud.tree.CloudNodeTreeNode
import org.modelix.mps.sync.tools.cloud.tree.CloudRootTreeNode
import org.modelix.mps.sync.tools.cloud.tree.ModelServerTreeNode
import org.modelix.mps.sync.tools.cloud.tree.RepositoryTreeNode
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.TreeNode

// status: migrated, but needs some bugfixes
class CloudView : JPanel(BorderLayout()) {

    private val tree = CloudViewTree()

    init {
        val scrollPane = JScrollPane(tree)
        scrollPane.setBorder(BorderFactory.createEmptyBorder())
        add(scrollPane, BorderLayout.CENTER)
        tree.rebuildLater()
    }

    class CloudViewTree : MPSTree(), DataProvider {

        public override fun runRebuildAction(rebuildAction: Runnable, saveExpansion: Boolean) {
            super.runRebuildAction(rebuildAction, saveExpansion)
        }

        override fun rebuild(): MPSTreeNode {
            val root = TextTreeNode("Loading ...")
            root.add(CloudRootTreeNode())
            setRootVisible(false)
            return root
        }

        override fun createPopupActionGroup(node: MPSTreeNode) =
            when (node) {
                is CloudRootTreeNode -> {
                    ActionUtils.groupFromActions(
                        ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.AddModelServerAction"),
                    )
                }

                is ModelServerTreeNode -> {
                    ActionUtils.groupFromActions(
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.modelServer.RemoveModelServer"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.modelServer.AddRepositoryAction"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.modelServer.ShowAuthenticationInfoAction"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.modelServer.ReconnectAction"),
                    )
                }

                is CloudNodeTreeNode -> {
                    ActionUtils.getGroup("org.modelix.mps.sync.actions.node.CloudNodeActionGroup")
                }

                is RepositoryTreeNode -> {
                    ActionUtils.groupFromActions(
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.repository.LoadHistoryForRepositoryAction"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.repository.RemoveRepositoryAction"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.repository.GetCloudRepositorySizeAction"),
                    )
                }

                is CloudBranchTreeNode -> {
                    ActionUtils.groupFromActions(
                        ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.branch.AddBranchAction"),
                        ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.branch.SwitchBranchAction"),
                        ActionManager.getInstance()
                            .getAction("org.modelix.mps.sync.actions.branch.LoadHistoryForBranchAction"),
                    )
                }

                is CloudBindingTreeNode -> {
                    ActionUtils.groupFromActions(
                        ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.unbind.UnbindAction"),
                    )
                }

                else -> {
                    null
                }
            }

        private fun <T : TreeNode?> getSelectedTreeNode(nodeClass: Class<T>): T? {
            val selectionPath = selectionPath ?: return null
            val selectedNode = selectionPath.lastPathComponent
            return if (nodeClass.isInstance(selectedNode)) nodeClass.cast(selectedNode) else null
        }

        override fun getData(dataId: String): Any? {
            return if (MPSCommonDataKeys.TREE_NODE.`is`(dataId)) {
                getSelectedTreeNode(TreeNode::class.java)!!
            } else {
                null
            }
        }
    }
}
