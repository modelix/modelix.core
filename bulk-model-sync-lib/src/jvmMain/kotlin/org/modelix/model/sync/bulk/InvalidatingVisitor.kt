/*
 * Copyright (c) 2024.
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

package org.modelix.model.sync.bulk

import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.data.NodeData

/**
 * Visitor that visits a [tree] and stores the invalidation information in an [invalidationTree].
 */
class InvalidatingVisitor(val tree: ITree, val invalidationTree: InvalidationTree) : ITreeChangeVisitorEx {

    private fun invalidateNode(nodeId: Long) = invalidationTree.invalidate(tree, nodeId)

    override fun containmentChanged(nodeId: Long) {
        // Containment can only change if also the children of the parent changed.
        // Synchronizing the parent will automatically update the containment of the children.
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        invalidateNode(nodeId)
    }

    override fun referenceChanged(nodeId: Long, role: String) {
        invalidateNode(nodeId)
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        if (role == NodeData.ID_PROPERTY_KEY) return
        invalidateNode(nodeId)
    }

    override fun nodeRemoved(nodeId: Long) {
        // same reason as in containmentChanged
    }

    override fun nodeAdded(nodeId: Long) {
        invalidateNode(nodeId)
    }
}
