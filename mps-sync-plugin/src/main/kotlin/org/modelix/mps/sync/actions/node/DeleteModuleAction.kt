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
import com.intellij.openapi.ui.Messages
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getMpsProject
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.util.delete
import org.modelix.mps.sync.actions.util.getName
import org.modelix.mps.sync.actions.util.isModuleNode
import javax.swing.Icon

class DeleteModuleAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent) = event.getTreeNode()?.isModuleNode() == true

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNode()
        val project = event.getMpsProject()!!
        val dialogResult: Int = Messages.showOkCancelDialog(
            project.project,
            "Are you sure you want to delete module '${treeNode?.getName()}' ?",
            "Delete Module",
            null,
        )
        if (dialogResult != Messages.OK) {
            return
        }
        treeNode?.delete()
    }
}
