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

import com.intellij.icons.AllIcons
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.icons.GlobalIconManager
import jetbrains.mps.ide.ui.tree.TextTreeNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.ContextArea
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.NodeAsMPSNode
import org.modelix.mps.sync.icons.LoadingIcon
import org.modelix.mps.sync.replication.CloudRepository
import org.modelix.mps.sync.util.CommandHelper
import org.modelix.mps.sync.util.createModuleInRepository
import org.modelix.mps.sync.util.createProject
import org.modelix.mps.sync.util.mappedMpsNodeID
import java.util.Collections
import javax.swing.tree.TreeNode

// status: migrated, but needs some bugfixes
/**
 * This represents a single node inside the CloudView
 */
class CloudNodeTreeNode(val branch: IBranch, val node: INode) : TextTreeNode("") {

    var concept: IConcept? = null
        private set
    private var initialized = false
    private val logger = mu.KotlinLogging.logger {}

    init {
        PArea(branch).executeRead {
            this.concept = node.concept
            val nodeId = (node as PNodeAdapter).nodeId
            nodeIdentifier = nodeId.toString()
            updateText()
        }
    }

    override fun isInitialized() = initialized

    override fun update() = doUpdate()

    override fun doUpdate() {
        TreeModelUtil.clearChildren(this)
        initialized = false
    }

    override fun isLeaf(): Boolean {
        return if (!initialized) {
            false
        } else {
            super.isLeaf()
        }
    }

    override fun doUpdatePresentation() {
        super.doUpdatePresentation()
        updateText()
    }

    override fun doInit() {
        super.doInit()
        initialized = true
        updateText()
        updateChildren()
    }

    fun getModelServer() = getAncestor(RepositoryTreeNode::class.java)?.modelServer

    fun setTextAndRepaint(text: String) = TreeModelUtil.setTextAndRepaint(this, text)

    private fun updateText() {
        ThreadUtils.runInUIThreadAndWait { LoadingIcon.apply(this) }
        SharedExecutors.FIXED.execute {
            PArea(branch).executeRead {
                var newText = ""
                var mappedMPSNodeID: String? = null
                val nodeId = (node as PNodeAdapter).nodeId
                if (nodeId == ITree.ROOT_ID) {
                    newText = "ROOT #1"
                    icon = AllIcons.Nodes.Folder
                } else {
                    val concept = node.concept
                    if (concept != null) {
                        mappedMPSNodeID = node.mappedMpsNodeID()
                        val snode = NodeAsMPSNode.wrap(node)!!
                        val mpsRepo = CommandHelper.getSRepository()
                        mpsRepo.modelAccess.runReadAction {
                            ContextArea.withAdditionalContext(MPSArea(mpsRepo)) {
                                newText = try {
                                    "${snode.presentation} [${concept.getLongName()}]   #${
                                        java.lang.Long.toHexString(nodeId)
                                    }"
                                } catch (ex: Exception) {
                                    logger.error(ex) { "Failed to update the text" }
                                    "!!!${ex.message}"
                                }
                                try {
                                    icon = GlobalIconManager.getInstance().getIconFor(snode)
                                } catch (ex: Exception) {
                                    logger.error(ex) { "Failed to update the icon" }
                                }
                            }
                        }
                    } else {
                        newText = "#$nodeId"
                    }
                }
                val role = node.roleInParent
                if (role != null) {
                    newText = "$role : $newText"
                }
                mappedMPSNodeID?.let { newText = "$newText -> MPS($it)" }
                ThreadUtils.runInUIThreadNoWait {
                    setTextAndRepaint(newText)
                }
            }
        }
    }

    private fun updateChildren() {
        if (!initialized) {
            throw RuntimeException()
        }

        val existing = mutableMapOf<INode, CloudNodeTreeNode>()
        ThreadUtils.runInUIThreadAndWait {
            if (!TreeModelUtil.getChildren(this).iterator().hasNext()) {
                TreeModelUtil.setChildren(
                    this,
                    // is it correct? TextTreeNode is not TreeNode --> TODO test if it is null at runtime
                    Collections.singleton(LoadingIcon.apply(TextTreeNode("loading ...")) as TreeNode),
                )
            }
            TreeModelUtil.getChildren(this).filterIsInstance<CloudNodeTreeNode>().forEach { existing[it.node] = it }
        }
        SharedExecutors.FIXED.execute {
            PArea(branch).executeRead {
                val newChildren = node.allChildren.map {
                    if (existing.containsKey(it)) {
                        existing[it]
                    } else {
                        CloudNodeTreeNode(branch, it)
                    }
                }.toList()
                ThreadUtils.runInUIThreadNoWait {
                    TreeModelUtil.setChildren(this, newChildren.filterIsInstance<TreeNode>())
                    newChildren.forEach { it?.update() }
                }
            }
        }
    }

    fun getTreeInRepository(): CloudRepository {
        val modelServer = this.getAncestor(ModelServerTreeNode::class.java).modelServer
        val repositoryId = this.getAncestor(RepositoryTreeNode::class.java).repositoryId
        return CloudRepository(modelServer, repositoryId)
    }

    // TODO check this represent a repository/a tree root
    fun createProject(moduleName: String) = (this.node as PNodeAdapter).createProject(moduleName)

    // TODO check this represent a repository/a tree root
    fun createModule(moduleName: String) = (this.node as PNodeAdapter).createModuleInRepository(moduleName)

    fun createModel(modelName: String): INode =
        PArea(branch).executeWrite {
            val newModel = this.node.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Module.models,
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.Model,
            )
            newModel.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, modelName)
            newModel
        }
}
