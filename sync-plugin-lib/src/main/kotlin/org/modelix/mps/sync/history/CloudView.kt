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

package org.modelix.mps.sync.history

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataProvider
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.ide.ui.tree.MPSTree
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import jetbrains.mps.ide.ui.tree.TextTreeNode
import jetbrains.mps.workbench.action.ActionUtils
import javax.swing.tree.TreeNode

class CloudView {

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

        override fun createPopupActionGroup(node: MPSTreeNode): ActionGroup? {
            // TODO fixme, this method relies on the ActionGroups. We have to migrate those Groups as well and set thhe correct ID here!
            if (node is CloudRootTreeNode) {
                return ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.CloudRootGroup_ActionGroup")
            }
            if (node is ModelServerTreeNode) {
                return ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.ModelServerGroup_ActionGroup")
            }
            if (node is CloudNodeTreeNode) {
                return ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.CloudNodeGroup_ActionGroup")
            }
            if (node is RepositoryTreeNode) {
                return ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.RepositoryGroup_ActionGroup")
            }
            if (node is CloudBranchTreeNode) {
                return ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.CloudBranchGroup_ActionGroup")
            }
            return if (node is CloudBindingTreeNode) {
                ActionUtils.getGroup("org.modelix.model.mpsplugin.plugin.CloudBindingGroup_ActionGroup")
            } else {
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
