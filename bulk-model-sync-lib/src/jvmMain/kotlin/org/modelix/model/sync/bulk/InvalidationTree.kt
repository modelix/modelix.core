package org.modelix.model.sync.bulk

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter

/**
 * The purpose of this data structure is to store which nodes changed and need to be synchronized,
 * but limit the memory consumption via the [sizeLimit], which determines the maximum number of nodes in a subtree.
 *
 * If there are many changes in one part of the model (changed subtree size > [sizeLimit]),
 * we do not keep track of the individual changes anymore and just synchronize the entire subtree.
 */
class InvalidationTree(val sizeLimit: Int) : ModelSynchronizer.IFilter {
    private val rootNode = Node(ITree.ROOT_ID)

    /**
     * Marks the node stored in the given containment path as changed.
     */
    fun invalidate(containmentPath: LongArray) {
        require(containmentPath[0] == ITree.ROOT_ID) { "Path must start with the root node" }
        rootNode.invalidate(containmentPath, 0)
        rootNode.rebalance(sizeLimit)
    }

    /**
     * Marks the node as changed.
     *
     * @param tree used internally for the calculation of the containment path
     * @param nodeId the id of the changed node
     */
    fun invalidate(tree: ITree, nodeId: Long) {
        val containmentPath = tree.ancestorsAndSelf(nodeId).toList().asReversed().toLongArray()
        invalidate(containmentPath)
    }

    override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean {
        require(subtreeRoot is PNodeAdapter)
        val path = subtreeRoot.branch.transaction.tree.ancestorsAndSelf(subtreeRoot.nodeId).toList().asReversed()
        return rootNode.needsDescentIntoSubtree(path, 0)
    }

    override fun needsSynchronization(node: INode): Boolean {
        require(node is PNodeAdapter)
        val path = node.branch.transaction.tree.ancestorsAndSelf(node.nodeId).toList().asReversed()
        return rootNode.nodeNeedsUpdate(path, 0)
    }

    private class Node(val id: Long) {
        private var subtreeSize = 1
        private var nodeNeedsUpdate: Boolean = false
        private var allDescendantsNeedUpdate = false
        private var invalidChildren: TLongObjectMap<Node> = TLongObjectHashMap()

        /**
         * @return number of added nodes
         */
        fun invalidate(path: LongArray, currentIndex: Int): Int {
            var addedNodesCount = 0
            if (currentIndex > path.lastIndex) {
                nodeNeedsUpdate = true
            } else {
                if (allDescendantsNeedUpdate) return addedNodesCount
                val childId = path[currentIndex]
                val child = invalidChildren.get(childId) ?: Node(childId).also {
                    invalidChildren.put(childId, it)
                    addedNodesCount++
                }
                addedNodesCount += child.invalidate(path, currentIndex + 1)
            }
            subtreeSize += addedNodesCount
            return addedNodesCount
        }

        fun rebalance(sizeLimit: Int) {
            // The size limit should be distributed equally across all children, but allow bigger children to use the
            // unused space of smaller children.

            if (sizeLimit >= subtreeSize) return // limit already fulfilled

            if ((sizeLimit - 1) < invalidChildren.size()) {
                // rebalancing not possible without removing nodes
                allDescendantsNeedUpdate = true
                invalidChildren = TLongObjectHashMap(0)
                subtreeSize = 1
                return
            }

            var remainingNodesToRemove = subtreeSize - sizeLimit

            val sortedChildren: List<Node> = invalidChildren.valueCollection().sortedByDescending { it.subtreeSize }
            val rebalancedSizes: IntArray = sortedChildren.map { it.subtreeSize }.toIntArray()
            while (remainingNodesToRemove > 0) {
                for (i in rebalancedSizes.indices) {
                    var childSizeLimit = (remainingNodesToRemove / (i + 1))
                    if (i != rebalancedSizes.lastIndex) childSizeLimit = childSizeLimit.coerceAtLeast(rebalancedSizes[i + 1])

                    for (k in 0..i) {
                        val delta = (rebalancedSizes[k] - childSizeLimit).coerceAtMost(remainingNodesToRemove)
                        rebalancedSizes[k] -= delta
                        remainingNodesToRemove -= delta
                    }
                }
            }

            for (i in sortedChildren.indices) {
                val child = sortedChildren[i]
                child.rebalance(rebalancedSizes[i])
            }

            subtreeSize = invalidChildren.valueCollection().sumOf { it.subtreeSize } + 1
        }

        fun needsDescentIntoSubtree(path: List<Long>, currentIndex: Int): Boolean {
            if (allDescendantsNeedUpdate) return true
            if (currentIndex < path.size) {
                val child = invalidChildren[path[currentIndex]] ?: return false
                return child.needsDescentIntoSubtree(path, currentIndex + 1)
            } else {
                return invalidChildren.size() > 0
            }
        }

        fun nodeNeedsUpdate(path: List<Long>, currentIndex: Int): Boolean {
            if (allDescendantsNeedUpdate) return true
            if (currentIndex < path.size) {
                val child = invalidChildren[path[currentIndex]] ?: return false
                return child.nodeNeedsUpdate(path, currentIndex + 1)
            } else {
                return nodeNeedsUpdate
            }
        }
    }
}

private fun ITree.ancestorsAndSelf(nodeId: Long): Sequence<Long> {
    return generateSequence(nodeId) { previousId -> getParent(previousId).takeIf { it != 0L } }
}
