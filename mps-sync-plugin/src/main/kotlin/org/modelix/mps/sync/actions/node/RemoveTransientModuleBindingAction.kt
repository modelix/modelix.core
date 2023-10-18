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

import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.actions.util.getTransientModuleBinding
import org.modelix.mps.sync.actions.util.isBoundAsModule
import org.modelix.mps.sync.actions.util.isModuleNode
import org.modelix.mps.sync.configuration.PersistedBindingConfiguration
import org.modelix.mps.sync.tools.cloud.tree.CloudNodeTreeNode
import org.modelix.mps.sync.tools.cloud.tree.ModelServerTreeNode
import org.modelix.mps.sync.tools.cloud.tree.RepositoryTreeNode
import javax.swing.Icon

class RemoveTransientModuleBindingAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent): Boolean {
        val treeNode = event.getTreeNode()
        return treeNode?.isModuleNode() == true && treeNode.isBoundAsModule()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        val modelServerConnection = treeNode.getAncestor(ModelServerTreeNode::class.java).modelServer
        val repositoryId = treeNode.getAncestor(RepositoryTreeNode::class.java).repositoryId
        val transientModuleBinding = treeNode.getTransientModuleBinding()!!
        modelServerConnection.removeBinding(transientModuleBinding)

        val project = event.project!!
        val treeInRepository = CloudRepository(modelServerConnection, repositoryId)
        PersistedBindingConfiguration.getInstance(project).removeTransientBoundModule(treeInRepository, treeNode)
    }
}
