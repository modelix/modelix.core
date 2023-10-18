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
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.cloud.tree.RepositoryTreeNode
import org.modelix.mps.sync.tools.history.HistoryToolFactory
import javax.swing.Icon

class LoadHistoryForRepositoryAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<RepositoryTreeNode>()
        val activeBranch = treeNode.modelServer.getActiveBranch(treeNode.repositoryId)
        HistoryToolFactory().load(treeNode.modelServer, treeNode.repositoryId) { activeBranch.version }
    }
}
