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
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.actions.util.isBoundAsModule
import org.modelix.mps.sync.actions.util.isModuleNode
import org.modelix.mps.sync.configuration.PersistedBindingConfiguration
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import org.modelix.mps.sync.tools.history.RepositoryTreeNode
import org.modelix.mps.sync.transient.TransientModuleBinding
import javax.swing.Icon

class AddTransientModuleBinding : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val isApplicable = treeNode?.isModuleNode() == true && !treeNode.isBoundAsModule()
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        val modelServerConnection = treeNode.getAncestor(ModelServerTreeNode::class.java).modelServer
        val repositoryId = treeNode.getAncestor(RepositoryTreeNode::class.java).repositoryId
        val transientModuleBinding = TransientModuleBinding((treeNode.node as PNodeAdapter).nodeId)
        modelServerConnection.addBinding(repositoryId, transientModuleBinding)
        val treeInRepository = CloudRepository(modelServerConnection, repositoryId)

        val project = event.dataContext.getData(CommonDataKeys.PROJECT) as Project
        PersistedBindingConfiguration.getInstance(project).addTransientBoundModule(treeInRepository, treeNode)
    }
}
