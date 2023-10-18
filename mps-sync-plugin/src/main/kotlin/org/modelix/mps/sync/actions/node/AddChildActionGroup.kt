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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getAllSubConcepts
import org.modelix.mps.sync.actions.getMpsProject
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode

class AddChildActionGroup : ActionGroup() {

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        if (event?.getTreeNode() == null || event.getMpsProject() == null) {
            return emptyArray()
        }

        val treeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        val node = treeNode.node
        if (node !is PNodeAdapter) {
            return emptyArray()
        }
        val concept = node.concept ?: return emptyArray()

        val actions = mutableListOf<AnAction>()
        for (role in concept.getAllChildLinks()) {
            if (role == BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.smodelAttribute) {
                continue
            }

            var subConcepts = role.targetConcept.getAllSubConcepts(true).filter { !it.isAbstract() }
            if (role == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes) {
                // TODO filter it.isRootable()
                subConcepts = subConcepts.filter { true }
            }
            subConcepts.sortedBy { it.getLongName() }.forEach { actions.add(AddChildNodeAction(node, it, role)) }
        }

        return actions.toTypedArray()
    }
}
