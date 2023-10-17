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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import jetbrains.mps.util.NameUtil
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.icons.CloudIcons
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import javax.swing.Icon

class AddRepositoryAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun actionPerformed(event: AnActionEvent) {
        val modelServer = event.getTreeNodeAs<ModelServerTreeNode>().modelServer

        val project = event.project
        val id = Messages.showInputDialog(project, "ID", "Add Repository", CloudIcons.REPOSITORY_ICON)
        if (id.isNullOrEmpty()) {
            return
        }
        modelServer.addRepository(NameUtil.toValidIdentifier(id))
    }
}
