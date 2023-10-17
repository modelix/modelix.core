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
import org.modelix.mps.sync.actions.util.delete
import org.modelix.mps.sync.actions.util.getName
import org.modelix.mps.sync.actions.util.isProjectNode
import javax.swing.Icon

class DeleteProject : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val isApplicable = treeNode?.isProjectNode() == true
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val project = event.dataContext.getData(MPSCommonDataKeys.MPS_PROJECT) as MPSProject
        val dialogResult: Int = Messages.showOkCancelDialog(
            project.project,
            "Are you sure you want to delete project '${treeNode?.getName()}' ?",
            "Delete Project",
            null,
        )
        if (dialogResult != Messages.OK) {
            return
        }
        treeNode?.delete()
    }
}
