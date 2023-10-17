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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.modelix.model.api.IProperty
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.actions.util.isProperNode
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.Icon

class ShowPropertiesAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent) = event.getTreeNode()?.isProperNode() == true

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        val treeInRepository = treeNode.getTreeInRepository()
        // I need to know in which module to look for this node

        val sb = StringBuilder()
        treeInRepository.runRead { ->
            val node = treeNode.node
            val properties = node.concept?.getAllProperties()
            val combined: LinkedHashSet<IProperty> = LinkedHashSet()
            properties?.let { combined.addAll(properties) }
            combined.addAll(node.getPropertyLinks())

            combined.forEach { property ->
                sb.append(property.getSimpleName())
                sb.append(" = ")
                sb.append(node.getPropertyValue(property))
                sb.append("\n")
            }
        }

        val project = event.dataContext.getData(CommonDataKeys.PROJECT) as Project
        Messages.showMessageDialog(project, sb.toString(), "Properties", null)
    }
}
