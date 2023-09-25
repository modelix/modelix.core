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
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.connection.IModelServerConnectionListener
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.history.TreeModelUtil.setTextAndRepaint
import org.modelix.mps.sync.icons.CloudIcons
import org.modelix.mps.sync.icons.LoadingIcon
import org.modelix.mps.sync.util.ModelixNotifications
import java.util.Collections
import javax.swing.SwingUtilities
import javax.swing.tree.TreeNode

// status: migrated, but needs some bugfixes
class ModelServerTreeNode(val modelServer: ModelServerConnection) :
    TextTreeNode(CloudIcons.MODEL_SERVER_ICON, modelServer.baseUrl) {

    private var infoBranch: IBranch? = null

    private val branchListener = object : IBranchListener {
        override fun treeChanged(oldTree: ITree?, newTree: ITree) {
            SwingUtilities.invokeLater {
                (getTree() as CloudView.CloudViewTree).runRebuildAction({
                    updateChildren()
                }, true)
            }
        }
    }

    private val repoListener = object : IModelServerConnectionListener {
        override fun connectionStatusChanged(connected: Boolean) {
            SwingUtilities.invokeLater {
                if (connected) {
                    infoBranch = modelServer.getInfoBranch()
                    if (getTree() != null) {
                        infoBranch?.addListener(branchListener)
                    }
                }
                updateText()
                updateChildren()
            }
        }
    }

    init {
        setAllowsChildren(true)
        nodeIdentifier = System.identityHashCode(modelServer).toString()
        modelServer.addListener(repoListener)
        updateText()
        updateChildren()
    }

    fun updateText() {
        var text = modelServer.baseUrl
        text += if (modelServer.isConnected()) {
            " (${modelServer.id})"
        } else {
            " (not connected)"
        }
        val email = modelServer.email
        if (email?.isNotEmpty() == true) {
            text += " $email"
        }
        setTextAndRepaint(text)
    }

    fun setTextAndRepaint(text: String) = setTextAndRepaint(this, text)

    private fun updateChildren() {
        if (modelServer.isConnected()) {
            // TODO fixme first parameter must be node<org.modelix.model.runtimelang.structure.RepositoryInfo>
            val existing = mutableMapOf<RepositoryInfoPlaceholder, RepositoryTreeNode>()
            ThreadUtils.runInUIThreadAndWait {
                if (TreeModelUtil.getChildren(this).isEmpty()) {
                    TreeModelUtil.setChildren(
                        this,
                        Collections.singleton(LoadingIcon.apply(TextTreeNode("loading ..."))),
                    )
                }
                TreeModelUtil.getChildren(this).filterIsInstance<RepositoryTreeNode>().forEach {
                    existing[it.repositoryInfo] = it
                }
            }

            SharedExecutors.FIXED.execute {
                val info = modelServer.getInfo()
                val newChildren = PArea(modelServer.getInfoBranch()).executeRead {
                    info.repositories.map {
                        var tn: TreeNode? = null
                        try {
                            tn = if (existing.containsKey(it)) {
                                existing[it]
                            } else {
                                RepositoryTreeNode(modelServer, it)
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            ModelixNotifications.notifyError(
                                "Repository in invalid state",
                                "Repository ${it.id} cannot be loaded: ${t.message}",
                            )
                        }
                        tn
                    }.filterNotNull()
                }

                ThreadUtils.runInUIThreadNoWait {
                    TreeModelUtil.setChildren(this, newChildren)
                    TreeModelUtil.getChildren(this).filterIsInstance<RepositoryTreeNode>()
                        .forEach { it.updateBranches() }
                }
            }
        } else {
            ThreadUtils.runInUIThreadNoWait {
                TreeModelUtil.clearChildren(this)
            }
        }
    }

    override fun onAdd() {
        super.onAdd()
        if (infoBranch != null) {
            infoBranch!!.addListener(branchListener)
        }
    }

    override fun onRemove() {
        super.onRemove()
        if (infoBranch != null) {
            modelServer.getInfoBranch().removeListener(branchListener)
        }
    }
}
