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
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.project.MPSProject
import org.modelix.mps.sync.actions.util.isModuleNode
import org.modelix.mps.sync.tools.ModelCloudImportUtils
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import javax.swing.Icon

class CheckoutAndSyncModule : AnAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun update(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE)
        val isApplicable = treeNode?.isModuleNode() == true
        this.templatePresentation.isEnabled = isApplicable
    }

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.dataContext.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode
        val project = event.dataContext.getData(MPSCommonDataKeys.MPS_PROJECT) as MPSProject
        ModelCloudImportUtils.checkoutAndSync(treeNode, project)
    }
}
