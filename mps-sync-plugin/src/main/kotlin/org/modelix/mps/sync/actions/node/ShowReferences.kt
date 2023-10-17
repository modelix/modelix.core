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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.actions.util.isProperNode
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.Icon

class ShowReferences : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val isApplicable = treeNode?.isProperNode() == true
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        val treeInRepository = treeNode.getTreeInRepository()
        // I need to know in which module to look for this node

        val sb = StringBuilder()
        treeInRepository.runRead { ->
            val node = treeNode.node
            val referenceLinks = node.concept?.getAllReferenceLinks()

            val combined: LinkedHashSet<IReferenceLink> = LinkedHashSet()
            referenceLinks?.let { combined.addAll(referenceLinks) }
            combined.addAll(node.getReferenceLinks())

            combined.forEach { refLink ->
                sb.append(refLink.getSimpleName())
                sb.append(" -> ")
                val target = node.getReferenceTarget(refLink)
                if (target is PNodeAdapter) {
                    if (target.branch != treeNode.branch) {
                        sb.append("[branch ${target.branch.getId()}] ")
                    }
                    sb.append("#")
                    sb.append(java.lang.Long.toHexString(target.nodeId))
                } else {
                    sb.append(target)
                }
                sb.append("\n")
            }
        }

        val project = event.dataContext.getData(CommonDataKeys.PROJECT) as Project
        Messages.showMessageDialog(project, sb.toString(), "References", null)
    }
}
