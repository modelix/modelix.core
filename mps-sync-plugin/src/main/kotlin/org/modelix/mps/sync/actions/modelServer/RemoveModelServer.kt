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

package org.modelix.mps.sync.actions.modelServer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import org.modelix.mps.sync.configuration.PersistedBindingConfiguration
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.icons.CloudIcons
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import javax.swing.Icon

class RemoveModelServer : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val isApplicable = treeNode is ModelServerTreeNode
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val modelServer = (treeNode as ModelServerTreeNode).modelServer

        val project = event.dataContext.getData(CommonDataKeys.PROJECT) as Project
        val answer: Int = Messages.showOkCancelDialog(
            project,
            "Remove ${modelServer.baseUrl}?",
            "Remove Model Server",
            "Remove",
            "Keep",
            CloudIcons.REPOSITORY_ICON,
        )
        if (answer == Messages.OK) {
            ModelServerConnections.instance.removeModelServer(modelServer)
            PersistedBindingConfiguration.getInstance(project).removeModelServer(modelServer)
        }
    }
}
