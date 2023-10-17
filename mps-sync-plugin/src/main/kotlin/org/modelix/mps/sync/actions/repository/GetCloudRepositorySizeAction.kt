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

package org.modelix.mps.sync.actions.repository

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.unwrap
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.RepositoryTreeNode
import javax.swing.Icon

class GetCloudRepositorySizeAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<RepositoryTreeNode>()
        val activeBranch = treeNode.modelServer.getActiveBranch(treeNode.repositoryId)
        val branch = activeBranch.branch
        val size = branch.computeRead {
            val tree = branch.transaction.tree.unwrap()
            if (tree is CLTree) {
                tree.getSize()
            } else {
                0L
            }
        }

        val project = event.project
        Messages.showInfoMessage(project, "Size is $size", "Size of Repository")
    }
}
