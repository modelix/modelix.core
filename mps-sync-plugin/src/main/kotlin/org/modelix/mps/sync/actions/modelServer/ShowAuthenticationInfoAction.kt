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
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import javax.swing.Icon

class ShowAuthenticationInfoAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent) = event.getTreeNode() is ModelServerTreeNode

    override fun actionPerformed(event: AnActionEvent) {
        val modelServer = event.getTreeNodeAs<ModelServerTreeNode>().modelServer
        val author = modelServer.getAuthor()
        val email = modelServer.email

        val project = event.project
        Messages.showInfoMessage(project, "Author: $author\nEmail: $email", "Authentication Info")
    }
}
