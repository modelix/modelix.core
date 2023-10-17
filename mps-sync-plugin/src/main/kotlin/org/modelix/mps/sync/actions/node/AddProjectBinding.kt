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

package org.modelix.mps.sync.actions.node

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.project.MPSProject
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.area.PArea
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.actions.util.isProjectNode
import org.modelix.mps.sync.importToCloud.ModelCloudImportUtils
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import org.modelix.mps.sync.tools.history.RepositoryTreeNode
import org.modelix.mps.sync.util.nodeIdAsLong
import javax.swing.Icon

class AddProjectBinding : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        var isApplicable = true

        if (!treeNode.isProjectNode()) {
            isApplicable = false
        } else {
            val nodeId = treeNode.node.nodeIdAsLong()
            val repositoryId = treeNode.getAncestor(RepositoryTreeNode::class.java).repositoryId
            if (treeNode.getModelServer()?.hasProjectBinding(repositoryId, nodeId) == true) {
                isApplicable = false
            }
        }

        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val namedProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name

        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        val expectedProjectName =
            PArea(treeNode.branch).executeRead { treeNode.node.getPropertyValue(namedProperty) }

        val project = event.dataContext.getData(MPSCommonDataKeys.MPS_PROJECT) as MPSProject
        if (expectedProjectName != project.name) {
            val dialogResult = Messages.showOkCancelDialog(
                project.project,
                "Project names don't match. Do you want to bind '$expectedProjectName' to '${project.name}'?",
                "Bind Project",
                null,
            )
            if (dialogResult != Messages.OK) {
                return
            }
        }
        val modelServer = treeNode.getAncestor(ModelServerTreeNode::class.java).modelServer
        val repositoryId = treeNode.getAncestor(RepositoryTreeNode::class.java).repositoryId
        val treeInRepository = CloudRepository(modelServer, repositoryId)
        val cloudProjectId = treeNode.node.nodeIdAsLong()
        ModelCloudImportUtils.bindCloudProjectToMpsProject(
            treeInRepository,
            cloudProjectId,
            project,
            SyncDirection.TO_MPS,
        )
    }
}
