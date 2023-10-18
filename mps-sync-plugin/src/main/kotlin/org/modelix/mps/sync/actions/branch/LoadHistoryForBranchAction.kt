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

package org.modelix.mps.sync.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.model.lazy.CLVersion
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.tools.cloud.tree.CloudBranchTreeNode
import org.modelix.mps.sync.tools.cloud.tree.RepositoryTreeNode
import org.modelix.mps.sync.tools.history.HistoryToolFactory
import javax.swing.Icon
import javax.swing.SwingUtilities

class LoadHistoryForBranchAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<CloudBranchTreeNode>()
        val treeTNode = treeNode.getAncestor(RepositoryTreeNode::class.java)
        val infoBranch = treeTNode.modelServer.getInfoBranch()
        val branchName =
            PArea(infoBranch).executeRead { treeNode.branchInfo.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) }
        val repositoryId = treeTNode.repositoryId
        val branchKey = repositoryId.getBranchReference(branchName).getKey()
        val modelServer = treeTNode.modelServer
        val client = modelServer.getClient()

        SharedExecutors.FIXED.execute {
            val versionHash = client[branchKey]!!
            val version = CLVersion.Companion.loadFromHash(versionHash, client.storeCache)
            SwingUtilities.invokeLater {
                HistoryToolFactory().load(modelServer, repositoryId) { version }
            }
        }
    }
}
