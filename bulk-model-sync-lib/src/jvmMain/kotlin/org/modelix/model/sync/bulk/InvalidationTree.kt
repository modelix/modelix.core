package org.modelix.model.sync.bulk

import org.modelix.model.api.IModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.ancestors
import org.modelix.model.api.toSerialized

/**
 * The purpose of this data structure is to store which nodes changed and need to be synchronized,
 * but limit the memory consumption via the [sizeLimit], which determines the maximum number of nodes in a subtree.
 *
 * If there are many changes in one part of the model (changed subtree size > [sizeLimit]),
 * we do not keep track of the individual changes anymore and just synchronize the entire subtree.
 */
class InvalidationTree(sizeLimit: Int = 100_000) : GenericInvalidationTree<Long, ITree>(ITree.ROOT_ID, sizeLimit) {
    override fun ancestorsAndSelf(model: ITree, nodeId: Long): List<Long> {
        return model.ancestorsAndSelf(nodeId).toList()
    }

    override fun getId(node: IReadableNode): Long {
        val subtreeRoot = node.asLegacyNode()
        require(subtreeRoot is PNodeAdapter)
        return subtreeRoot.nodeId
    }
}

class DefaultInvalidationTree(val root: INodeReference, sizeLimit: Int = 100_000) :
    GenericInvalidationTree<INodeReference, IModel>(root, sizeLimit = sizeLimit) {
    override fun ancestorsAndSelf(
        model: IModel,
        nodeId: INodeReference,
    ): List<INodeReference> {
        return model.tryResolveNode(nodeId)
            ?.ancestors(includeSelf = true)
            ?.map { it.getNodeReference().toSerialized() }
            ?.toList()
            ?: emptyList()
    }

    override fun getId(node: IReadableNode): NodeReference {
        return node.getNodeReference().toSerialized()
    }
}

abstract class GenericInvalidationTree<ID, M>(root: ID, val sizeLimit: Int = 100_000) : ModelSynchronizer.IIncrementalUpdateInformation {
    private val rootNode = Node(root)

    abstract fun ancestorsAndSelf(model: M, nodeId: ID): List<ID>
    abstract fun getId(node: IReadableNode): ID

    private fun getContainmentPath(node: IReadableNode) = node.ancestors(includeSelf = true).map { getId(it) }.toList().asReversed()

    /**
     * Marks the node stored in the given containment path as changed.
     */
    fun invalidate(containmentPath: List<ID>, includingDescendants: Boolean = false) {
        require(containmentPath[0] == rootNode.id) { "Path must start with the root node. Expected: ${rootNode.id}, was: $containmentPath" }
        rootNode.invalidate(containmentPath, 1, includingDescendants)
        rootNode.rebalance(sizeLimit)
    }

    /**
     * Marks the node as changed.
     *
     * @param tree used internally for the calculation of the containment path
     * @param nodeId the id of the changed node
     */
    fun invalidate(node: IReadableNode, includingDescendants: Boolean = false) {
        invalidate(getContainmentPath(node), includingDescendants)
    }

    fun reset() {
        rootNode.reset()
    }

    fun hasAnyInvalidations(): Boolean = rootNode.hasAnyInvalidations()

    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
        return needsDescentIntoSubtree(getContainmentPath(subtreeRoot))
    }

    fun needsDescentIntoSubtree(containmentPath: List<ID>): Boolean {
        require(containmentPath[0] == rootNode.id) { "Path must start with the root node. Expected: ${rootNode.id}, was: $containmentPath" }
        return rootNode.needsDescentIntoSubtree(containmentPath, 1)
    }

    override fun needsSynchronization(node: IReadableNode): Boolean {
        return needsSynchronization(getContainmentPath(node))
    }

    fun needsSynchronization(containmentPath: List<ID>): Boolean {
        require(containmentPath[0] == rootNode.id) { "Path must start with the root node. Expected: ${rootNode.id}, was: $containmentPath" }
        return rootNode.nodeNeedsUpdate(containmentPath, 1)
    }

    fun size() = rootNode.size()

    private class Node<E>(val id: E) {
        private var subtreeSize = 1
        private var nodeNeedsUpdate: Boolean = false
        private var allDescendantsNeedUpdate = false
        private var invalidChildren: MutableMap<E, Node<E>> = HashMap()

        fun hasAnyInvalidations() = nodeNeedsUpdate || allDescendantsNeedUpdate || invalidChildren.isNotEmpty()

        fun size() = subtreeSize

        fun reset() {
            subtreeSize = 1
            nodeNeedsUpdate = false
            allDescendantsNeedUpdate = false
            invalidChildren = HashMap()
        }

        /**
         * @return number of added nodes
         */
        fun invalidate(path: List<E>, currentIndex: Int, includingDescendants: Boolean): Int {
            var addedNodesCount = 0
            if (currentIndex > path.lastIndex) {
                nodeNeedsUpdate = true
                if (includingDescendants) {
                    addedNodesCount -= subtreeSize - 1
                    subtreeSize = 1
                    allDescendantsNeedUpdate = true
                    if (invalidChildren.isNotEmpty()) {
                        invalidChildren = HashMap(0)
                    }
                }
            } else {
                if (allDescendantsNeedUpdate) return addedNodesCount
                val childId = path[currentIndex]
                val child = invalidChildren.get(childId) ?: Node(childId).also {
                    invalidChildren.put(childId, it)
                    addedNodesCount++
                }
                addedNodesCount += child.invalidate(path, currentIndex + 1, includingDescendants)
            }
            subtreeSize += addedNodesCount
            return addedNodesCount
        }

        fun rebalance(sizeLimit: Int) {
            // The size limit should be distributed equally across all children, but allow bigger children to use the
            // unused space of smaller children.

            if (sizeLimit >= subtreeSize) return // limit already fulfilled

            if ((sizeLimit - 1) < invalidChildren.size) {
                // rebalancing not possible without removing nodes
                allDescendantsNeedUpdate = true
                invalidChildren = HashMap(0)
                subtreeSize = 1
                return
            }

            var remainingNodesToRemove = subtreeSize - sizeLimit

            val sortedChildren: List<Node<E>> = invalidChildren.values.sortedByDescending { it.subtreeSize }
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

            subtreeSize = invalidChildren.values.sumOf { it.subtreeSize } + 1
        }

        fun needsDescentIntoSubtree(path: List<E>, currentIndex: Int): Boolean {
            if (allDescendantsNeedUpdate) return true
            if (currentIndex < path.size) {
                val child = invalidChildren[path[currentIndex]] ?: return false
                return child.needsDescentIntoSubtree(path, currentIndex + 1)
            } else {
                return invalidChildren.size > 0
            }
        }

        fun nodeNeedsUpdate(path: List<E>, currentIndex: Int): Boolean {
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
