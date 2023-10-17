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

package org.modelix.mps.sync.actions.util

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.area.PArea
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.tree.TreeNode

fun TreeNode.isRootNode(): Boolean {
    val nodeTreeNode = this as? CloudNodeTreeNode
    return nodeTreeNode?.isCloudNodeRootNode() ?: false
}

fun TreeNode.isModuleNode(): Boolean {
    val nodeTreeNode = this as? CloudNodeTreeNode
    return nodeTreeNode?.isCloudNodeModuleNode() ?: false
}

fun TreeNode.isProjectNode(): Boolean {
    val nodeTreeNode = this as? CloudNodeTreeNode
    return nodeTreeNode?.isCloudNodeAProjectNode() ?: false
}

/**
 * This does not consider if this is a module, and it is bound indirectly because the whole project is bound.
 */
fun TreeNode.isBoundAsModule(): Boolean {
    val nodeTreeNode = this as? CloudNodeTreeNode
    return nodeTreeNode?.isBoundAsAModule() ?: false
}

fun TreeNode.getName(): String? {
    val nodeTreeNode = this as CloudNodeTreeNode
    return PArea(nodeTreeNode.branch).executeRead { nodeTreeNode.node.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) }
}

fun TreeNode.delete() {
    val nodeTreeNode = this as CloudNodeTreeNode
    val parent = nodeTreeNode.parent
    PArea(nodeTreeNode.branch).executeWrite {
        val nodeIN = nodeTreeNode.node
        val parentIN = nodeIN.parent
        if (parentIN == null) {
            var found = false
            nodeTreeNode.getModelServer()?.trees()?.forEach { tree ->
                if (tree.repoRoots().contains(nodeIN)) {
                    tree.deleteRoot(nodeIN)
                    found = true
                }
            }
            if (found) {
                return@executeWrite
            } else {
                throw RuntimeException("Unable to remove node without parent, not found as root of any tree")
            }
        }
        parentIN.removeChild(nodeIN)
    }

    check(parent != null) { "Cannot remove node without parent" }

    if (parent is CloudNodeTreeNode) {
        parent.remove(nodeTreeNode)
    } else {
        throw RuntimeException("Unable to remove child from parent $parent (${parent::class.java})")
    }
}
