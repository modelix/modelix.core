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

package org.modelix.mps.sync.tools.cloud.tree

import jetbrains.mps.ide.ui.tree.TextTreeNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.area.PArea
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.icons.CloudIcons

// status: migrated, but needs some bugfixes
class CloudBranchTreeNode(
    private val modelServer: ModelServerConnection,
    val branchInfo: INode,
) : TextTreeNode(
    CloudIcons.BRANCH_ICON,
    branchInfo.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name),
) {

    init {
        setAllowsChildren(true)
    }

    override fun doubleClick() {
        switchBranch()
    }

    fun switchBranch() {
        val treeTreeNode = this.getAncestor(RepositoryTreeNode::class.java)
        val repositoryId = treeTreeNode.repositoryId
        val infoBranch = modelServer.getInfoBranch()
        val branchName =
            PArea(infoBranch).executeRead { branchInfo.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)!! }
        modelServer.getActiveBranch(repositoryId).switchBranch(branchName)
    }
}
