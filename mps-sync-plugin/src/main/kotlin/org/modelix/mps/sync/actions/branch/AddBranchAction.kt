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

package org.modelix.mps.sync.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.area.PArea
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.CloudBranchTreeNode
import org.modelix.mps.sync.tools.history.RepositoryTreeNode
import javax.swing.Icon

class AddBranchAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val name = Messages.showInputDialog(project, "Name", "Add Branch", null)
        if (name.isNullOrEmpty()) {
            return
        }
        val treeNode = event.getTreeNodeAs<CloudBranchTreeNode>()
        val treeTreeNode = treeNode.getAncestor(RepositoryTreeNode::class.java)
        val repositoryId = treeTreeNode.repositoryId
        val modelServer = treeTreeNode.modelServer
        val infoBranch = modelServer.getInfoBranch()
        PArea(infoBranch).executeWrite {
            val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
            val treeInfo = treeTreeNode.repositoryInfo
            if (treeInfo.getChildren(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.branches)
                    .any { it.getPropertyValue(nameProperty) == name }
            ) {
                Messages.showErrorDialog(project, "Branch '$name' already exists", "Add Branch")
                return@executeWrite
            }
            val versionHash = modelServer.getClient()
                .get(repositoryId.getBranchKey(treeNode.branchInfo.getPropertyValue(nameProperty)))
            modelServer.getClient().put(repositoryId.getBranchReference(name).getKey(), versionHash)

            val index = treeInfo.getChildren(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.branches).count()
            val branchInfo = treeInfo.addNewChild(
                BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.branches,
                index,
                BuiltinLanguages.ModelixRuntimelang.BranchInfo,
            )
            branchInfo.setPropertyValue(nameProperty, name)
        }
    }
}
