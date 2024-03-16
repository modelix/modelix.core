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

package org.modelix.mps.model.sync.bulk

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.ancestorsAndSelf
import org.modelix.model.sync.bulk.ModelSynchronizer

/**
 * The purpose of this data structure is to store which nodes changes and need to be synchronized,
 * but limit the memory consumption.
 * If there are many changes in one part of the model we don't remember all the details, but just synchronize
 */
class InvalidationTree(val sizeLimit: Int) : ModelSynchronizer.IFilter {
    private val rootNode = Node(ITree.ROOT_ID)

    fun invalidate(containmentPath: LongArray) {
        require(containmentPath[0] == ITree.ROOT_ID) { "Path should start with the root node" }
        rootNode.invalidate(containmentPath, 1)
        rootNode.rebalance(sizeLimit)
    }

    override fun descendIntoSubtree(node: INode): Boolean {
        require(node is PNodeAdapter)
        val path = node.branch.transaction.tree.ancestorsAndSelf(node.nodeId).toList().asReversed()
        return rootNode.descendIntoSubtree(path, 0)
    }

    override fun synchronizeNode(node: INode): Boolean {
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
            }  else {
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

                    for (k in 0 .. i) {
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

        fun descendIntoSubtree(path: List<Long>, currentIndex: Int): Boolean {
            if (allDescendantsNeedUpdate) return true
            if (currentIndex < path.lastIndex) {
                val child = invalidChildren[path[currentIndex]] ?: return false
                return child.descendIntoSubtree(path, currentIndex + 1)
            } else {
                return invalidChildren.size() > 0
            }
        }

        fun nodeNeedsUpdate(path: List<Long>, currentIndex: Int): Boolean {
            if (allDescendantsNeedUpdate) return true
            if (currentIndex < path.lastIndex) {
                val child = invalidChildren[path[currentIndex]] ?: return false
                return child.nodeNeedsUpdate(path, currentIndex + 1)
            } else {
                return nodeNeedsUpdate
            }
        }
    }
}

