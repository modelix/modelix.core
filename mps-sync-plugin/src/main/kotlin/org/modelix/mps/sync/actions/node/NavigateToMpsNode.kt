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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.openapi.navigation.NavigationSupport
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.mps.sync.actions.util.isProperNode
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.util.containingModel
import org.modelix.mps.sync.util.containingModule
import org.modelix.mps.sync.util.isMappedToMpsNode
import org.modelix.mps.sync.util.mappedMpsNodeID
import javax.swing.Icon

class NavigateToMpsNode : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        var isApplicable = treeNode?.isProperNode() == true
        if (isApplicable) {
            val nodeTreeNode = treeNode as CloudNodeTreeNode
            val treeInRepository = nodeTreeNode.getTreeInRepository()
            isApplicable = treeInRepository.computeRead { nodeTreeNode.node.isMappedToMpsNode() }
        }
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        val project = event.dataContext.getData(CommonDataKeys.PROJECT) as Project
        val mpsProject = event.dataContext.getData(MPSCommonDataKeys.MPS_PROJECT) as MPSProject

        val treeInRepository = treeNode.getTreeInRepository()
        // I need to know in which module to look for this node

        treeInRepository.runRead { ->
            val mpsNodeId = treeInRepository.computeRead { treeNode.node.mappedMpsNodeID() }!!
            val cloudModule = treeNode.node.containingModule()
            if (cloudModule == null) {
                Messages.showErrorDialog(project, "No containing module found", "Error Navigating to MPS Node")
                return@runRead
            }
            val cloudModel = treeNode.node.containingModel()
            if (cloudModel == null) {
                Messages.showErrorDialog(project, "No containing model found", "Error Navigating to MPS Node")
                return@runRead
            }
            val moduleId = cloudModule.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id)
            if (moduleId == null) {
                Messages.showErrorDialog(project, "No module id", "Error Navigating to MPS Node")
                return@runRead
            }
            val modelId = cloudModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)
            if (modelId == null) {
                Messages.showErrorDialog(project, "No model id", "Error Navigating to MPS Node")
                return@runRead
            }
            val repo = mpsProject.repository
            repo.modelAccess.runReadAction {
                repo.modules.forEach { module ->
                    if (module.moduleId.toString() != moduleId) {
                        module.models.forEach { model ->
                            if (model.modelId.toString() == modelId) {
                                val node = this.findNodeInModel(model, mpsNodeId)
                                if (node == null) {
                                    Messages.showErrorDialog(
                                        project,
                                        "No node found: $mpsNodeId",
                                        "Error Navigating to MPS Node",
                                    )
                                } else {
                                    NavigationSupport.getInstance().openNode(mpsProject, node, false, true)
                                }
                                return@runReadAction
                            }
                        }
                        Messages.showErrorDialog(
                            project,
                            "No model found: $mpsNodeId",
                            "Error Navigating to MPS Node",
                        )
                        return@runReadAction
                    }
                }
                Messages.showErrorDialog(project, "No module found: $moduleId", "Error Navigating to MPS Node")
            }
        }
    }

    private fun findNodeInModel(model: SModel, nodeId: String): SNode? {
        model.rootNodes.forEach { root ->
            val res = this.findNodeInNode(root, nodeId)
            if (res != null) {
                return res
            }
        }
        return null
    }

    private fun findNodeInNode(node: SNode, nodeId: String): SNode? {
        if (node.nodeId.toString() == nodeId) {
            return node
        }
        node.children.forEach { child ->
            val res = this.findNodeInNode(child, nodeId)
            if (res != null) {
                return res
            }
        }
        return null
    }
}
