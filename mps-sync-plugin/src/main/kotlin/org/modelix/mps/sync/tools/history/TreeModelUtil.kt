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

package org.modelix.mps.sync.tools.history

import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.ui.tree.MPSTree
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import jetbrains.mps.util.IterableUtil
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

// status: ready to test
object TreeModelUtil {

    fun setChildren(parent: TreeNode, children: Iterable<TreeNode>) {
        val childrenList = children.toList()
        if (getChildren(parent).toList() == childrenList) {
            return
        }
        val wasExpanded = isExpanded(parent)
        clearChildren(parent)
        val model = getModel(parent) as DefaultTreeModel?
        if (model != null) {
            ThreadUtils.assertEDT()
            var i = 0
            childrenList.forEach { child ->
                model.insertNodeInto(child as MutableTreeNode, parent as MutableTreeNode, i)
                i++
            }
        } else {
            var i = 0
            childrenList.forEach { child ->
                (parent as MutableTreeNode).insert(child as MutableTreeNode, i)
                i++
            }
        }
        if (wasExpanded) {
            getTree(parent)?.expandPath(getPath(parent))
        }
    }

    fun getChildren(parent: TreeNode) =
        IterableUtil.asIterable(parent.children().asIterator()).filterIsInstance<TreeNode>()

    fun clearChildren(parent: TreeNode) {
        val model = getModel(parent) as DefaultTreeModel?
        if (model != null) {
            ThreadUtils.assertEDT()
            while (model.getChildCount(parent) > 0) {
                model.removeNodeFromParent(model.getChild(parent, 0) as MutableTreeNode)
            }
        } else {
            while (parent.childCount > 0) {
                (parent as MutableTreeNode).remove(0)
            }
        }
    }

    fun getModel(node: TreeNode): TreeModel? = getTree(node)?.model

    fun getTree(node: TreeNode): MPSTree? = if (node is MPSTreeNode) node.getTree() else null

    fun repaint(node: TreeNode) {
        ThreadUtils.runInUIThreadAndWait {
            getTree(node)?.repaint()
        }
    }

    fun setTextAndRepaint(node: MPSTreeNode, text: String) {
        node.text = text
        repaint(node)
    }

    fun isExpanded(node: TreeNode) = getTree(node)?.isExpanded(getPath(node)) == true

    fun getPath(node: TreeNode): TreePath {
        return if (node.parent == null) {
            TreePath(node)
        } else {
            getPath(node.parent).pathByAddingChild(node)
        }
    }
}
