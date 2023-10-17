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

package org.modelix.mps.sync.actions.unbind

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.modelix.mps.sync.binding.ModuleBinding
import org.modelix.mps.sync.configuration.PersistedBindingConfiguration
import org.modelix.mps.sync.tools.history.CloudBindingTreeNode
import javax.swing.Icon

class Unbind : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        // TODO testme how to get the tree node (possible workaround is to pass it via a setter method?)
        val treeNode = event.dataContext.getData("treeNode")
        val binding = (treeNode as CloudBindingTreeNode).binding
        val isApplicable = binding is ModuleBinding

        // Project binding cannot currently be removed
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        // TODO testme how to get the tree node (possible workaround is to pass it via a setter method?)
        val treeNode = event.dataContext.getData("treeNode")
        // Project binding cannot currently be removed
        val binding = (treeNode as CloudBindingTreeNode).binding as ModuleBinding
        val modelServer = treeNode.modelServer
        modelServer.removeBinding(binding)
        val repositoryInModelServer = treeNode.repositoryInModelServer

        // TODO testme how to get the project
        val project = event.dataContext.getData("project") as Project
        PersistedBindingConfiguration.getInstance(project).removeBoundModule(repositoryInModelServer, binding)
    }
}
