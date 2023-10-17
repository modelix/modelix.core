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

package org.modelix.mps.sync.actions.util

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.tools.history.RepositoryTreeNode

fun CloudNodeTreeNode.isCloudNodeRootNode(): Boolean {
    val node = this.node
    return if (node !is PNodeAdapter) {
        false
    } else {
        node.nodeId == ITree.ROOT_ID
    }
}

fun CloudNodeTreeNode.isCloudNodeModuleNode(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module)
}

fun CloudNodeTreeNode.isCloudNodeAProjectNode(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Project)
}

/**
 * This does not consider if this is a module, and it is bound indirectly because the whole project is bound.
 */
fun CloudNodeTreeNode.isBoundAsAModule(): Boolean {
    val nodeId = (this.node as PNodeAdapter).nodeId
    val repositoryId: RepositoryId = getAncestor(RepositoryTreeNode::class.java).repositoryId
    return getModelServer()!!.hasModuleBinding(repositoryId, nodeId)
}
