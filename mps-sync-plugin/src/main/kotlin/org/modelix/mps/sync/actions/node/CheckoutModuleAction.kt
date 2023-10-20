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
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getMpsProject
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.actions.util.isModuleNode
import org.modelix.mps.sync.exportFromCloud.ModuleCheckout
import org.modelix.mps.sync.replication.CloudRepository
import org.modelix.mps.sync.tools.cloud.tree.CloudNodeTreeNode
import javax.swing.Icon

class CheckoutModuleAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    // TODO verify it does not exist a module with such name
    override fun isApplicable(event: AnActionEvent) = event.getTreeNode()?.isModuleNode() == true

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        val treeInRepository: CloudRepository = treeNode.getTreeInRepository()

        val project = event.getMpsProject()!!
        ModuleCheckout(project, treeInRepository).checkoutCloudModule((treeNode.node as PNodeAdapter))
    }
}
