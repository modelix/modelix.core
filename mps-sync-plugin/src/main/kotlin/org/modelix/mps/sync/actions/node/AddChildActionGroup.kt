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
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SConceptOperations
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.language.SConcept
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.NodeAsMPSNode
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
        val concept = node.concept ?: return emptyArray()

        val snode = NodeAsMPSNode.wrap(node) ?: return emptyArray()
        val sconcept = snode.concept

        val project = event.getMpsProject() ?: return emptyArray()
        val allLanguages = LanguageRegistry.getInstance(project.repository).allLanguages.toMutableSet()

        val actions = mutableListOf<AnAction>()

        for (role in concept.getAllChildLinks()) {
            if (role == BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.smodelAttribute) {
                continue
            }
            var subConcepts = SConceptOperations.getAllSubConcepts(sconcept, allLanguages).filter { !it.isAbstract }
                .filterIsInstance(SConcept::class.java)
            if (role == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes) {
                subConcepts = subConcepts.filter { it.isRootable }
            }
            subConcepts.map { MPSConcept(it) }.sortedBy { it.getLongName() }
                .forEach { actions.add(AddChildNodeAction(node, it, role)) }
        }

        return actions.toTypedArray()
    }
}
