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
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.area.PArea
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.Icon

class AddChildNodeAction : ModelixAction {

    private var parentNode: INode? = null
    private var childConcept: IConcept? = null
    private var role: IChildLink? = null

    constructor() : super()

    constructor(parentNode: INode, childConcept: IConcept, role: IChildLink) : super() {
        this.parentNode = parentNode
        this.childConcept = childConcept
        this.role = role
    }

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent): Boolean {
        if (this.childConcept == null) {
            event.presentation.text = "To '${this.role?.getSimpleName()}'"
        } else {
            event.presentation.text = "To '${this.role?.getSimpleName()}' add '${this.childConcept?.getLongName()}'"
        }
        return true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name

        val nodeTreeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        var name: String? = null
        val hasNameProperty = (this.childConcept ?: return).getAllProperties().contains(nameProperty)

        if (hasNameProperty) {
            name = Messages.showInputDialog(event.project, "Name", "Add ${this.childConcept!!.getShortName()}", null)
        }
        if (name.isNullOrEmpty()) {
            return
        }

        PArea(nodeTreeNode.branch).executeWrite {
            val newModule = this.parentNode?.addNewChild(
                this.role?.getSimpleName(),
                -1,
                this.childConcept,
            )
            if (name.isNotEmpty()) {
                newModule?.setPropertyValue(nameProperty, name)
            }
        }
    }
}
