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
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.area.PArea
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.Icon

class SetPropertyAction : ModelixAction {

    private var node: INode? = null
    private var role: IProperty? = null

    constructor() : super()

    constructor(node: INode, role: IProperty) : super() {
        this.node = node
        this.role = role
    }

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent): Boolean {
        event.presentation.text = "Set Property '${this.role?.getSimpleName()}')"
        return true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val nodeTreeNode = event.getTreeNodeAs<CloudNodeTreeNode>()

        val currentValue =
            nodeTreeNode.getTreeInRepository().computeRead { nodeTreeNode.node.getPropertyValue(this.role!!) }
        val value = Messages.showInputDialog(
            event.project,
            "Value",
            "Set Property '${this.role?.getSimpleName()}'",
            null,
            currentValue,
            object : InputValidator {

                // TODO perhaps look into the type of the property to authorize it or not
                override fun checkInput(s: String): Boolean = true

                override fun canClose(s: String): Boolean = true
            },
        ) ?: return

        PArea(nodeTreeNode.branch).executeWrite {
            this.node?.setPropertyValue(this.role!!, value)
        }
    }
}
