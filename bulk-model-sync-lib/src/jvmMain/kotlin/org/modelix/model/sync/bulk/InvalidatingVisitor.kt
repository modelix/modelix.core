package org.modelix.model.sync.bulk

import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getNode

/**
 * Visitor that visits a [tree] and stores the invalidation information in an [invalidationTree].
 */
class InvalidatingVisitor(val newTree: ITree, val invalidationTree: InvalidationTree) : ITreeChangeVisitorEx {

    private fun invalidateNode(nodeId: Long) {
        invalidationTree.invalidate(TreePointer(newTree).getNode(nodeId).asReadableNode(), false)
    }

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
        invalidateNode(nodeId)
    }

    override fun nodeRemoved(nodeId: Long) {
        // same reason as in containmentChanged
    }

    override fun nodeAdded(nodeId: Long) {
        invalidateNode(nodeId)
    }
}
