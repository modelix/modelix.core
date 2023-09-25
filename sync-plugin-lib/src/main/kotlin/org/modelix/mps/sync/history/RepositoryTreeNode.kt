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

package org.modelix.mps.sync.history

import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.ui.tree.TextTreeNode
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.client.SharedExecutors
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.icons.CloudIcons
import org.modelix.mps.sync.icons.LoadingIcon
import java.util.Collections
import javax.swing.SwingUtilities

// status: migrated, but needs some bugfixes
class RepositoryTreeNode(
    modelServer: ModelServerConnection,
    repositoryInfo: RepositoryInfoPlaceholder, // TODO fixme, second parameters should be node<org.modelix.model.runtimelang.structure.RepositoryInfo>
) : TextTreeNode(
    CloudIcons.REPOSITORY_ICON,
    repositoryInfo.id,
) {

    val modelServer: ModelServerConnection
    val repositoryInfo: RepositoryInfoPlaceholder // TODO fixme, this should be node<org.modelix.model.runtimelang.structure.RepositoryInfo>
    val repositoryId: RepositoryId
    private val activeBranch: ActiveBranch
    private val dataTreeNode = TextTreeNode("data")
    private val branchesTreeNode = TextTreeNode("branches")
    private val bindingsTreeNode: CloudBindingTreeNode
    private val branchListener = object : IBranchListener {
        override fun treeChanged(oldTree: ITree?, newTree: ITree) {
            SwingUtilities.invokeLater {
                (getTree() as CloudView.CloudViewTree).runRebuildAction({
                    updateData()
                }, true)
            }
        }
    }

    init {
        try {
            this.modelServer = modelServer
            this.repositoryInfo = repositoryInfo
            this.repositoryId = RepositoryId(repositoryInfo.id)
            this.activeBranch = modelServer.getActiveBranch(RepositoryId(repositoryInfo.id))
            val cloudRepository = CloudRepository(modelServer, repositoryId)
            bindingsTreeNode = CloudBindingTreeNode(cloudRepository.getRootBinding(), cloudRepository)
            setAllowsChildren(true)
            TreeModelUtil.setChildren(
                this,
                listOf(dataTreeNode, branchesTreeNode, bindingsTreeNode),
            )
            activeBranch.addListener(branchListener)
            updateData()
        } catch (ex: RuntimeException) {
            throw RuntimeException(
                "Unable to initialize RepositoryTreeNode for repository with id ${repositoryInfo.id}",
                ex,
            )
        }
    }

    override fun onRemove() {
        super.onRemove()
        activeBranch.removeListener(branchListener)
    }

    fun updateBranches() {
        // TODO fixme first parameter must be node<org.modelix.model.runtimelang.structure.BranchInfo>
        val existing = mutableMapOf<BranchInfoPlaceholder, CloudBranchTreeNode>()
        ThreadUtils.runInUIThreadAndWait {
            if (TreeModelUtil.getChildren(this).isEmpty()) {
                TreeModelUtil.setChildren(this, Collections.singleton(LoadingIcon.apply(TextTreeNode("loading ..."))))
            }
            TreeModelUtil.getChildren(branchesTreeNode).filterIsInstance<CloudBranchTreeNode>().forEach { node ->
                existing[node.branchInfo] = node
            }
        }

        SharedExecutors.FIXED.execute {
            val newChildren = PArea(modelServer.getInfoBranch()).executeRead {
                repositoryInfo.branches.map {
                    if (existing.containsKey(it)) {
                        existing[it]!!
                    } else {
                        CloudBranchTreeNode(modelServer, it)
                    }
                }
            }
            ThreadUtils.runInUIThreadNoWait {
                TreeModelUtil.setChildren(branchesTreeNode, newChildren)
            }
        }
    }

    private fun updateData() {
        TreeModelUtil.setTextAndRepaint(dataTreeNode, "data [${activeBranch.branchName}]")
        val branch = activeBranch.branch
        val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
        val childTreeNodes = TreeModelUtil.getChildren(dataTreeNode).toList()
        if (childTreeNodes.size != 1 || (childTreeNodes.first() as CloudNodeTreeNode).node != rootNode) {
            val newTreeNode = CloudNodeTreeNode(branch, rootNode)
            TreeModelUtil.setChildren(dataTreeNode, newTreeNode.toList())
        }
        TreeModelUtil.getChildren(dataTreeNode).filterIsInstance<CloudNodeTreeNode>().forEach { it.update() }
    }
}

// TODO remove me and use node<org.modelix.model.runtimelang.structure.RepositoryInfo> instead
interface RepositoryInfoPlaceholder {
    val branches: Iterable<BranchInfoPlaceholder>
    val id: String
}

// TODO remove me and use node<org.modelix.model.runtimelang.structure.BranchInfo> instead
interface BranchInfoPlaceholder {
    val name: String
}
