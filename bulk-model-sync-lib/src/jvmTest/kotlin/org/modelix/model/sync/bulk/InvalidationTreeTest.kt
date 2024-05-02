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

import org.modelix.model.ModelFacade
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getAncestors
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvalidationTreeTest {

    @Test
    fun `invalidated node needs synchronization`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(testTree, 32L)

        val invalidatedNode = treePointer.computeRead { treePointer.getNode(32L) }
        assertTrue { invalidationTree.needsSynchronization(invalidatedNode) }
    }

    @Test
    fun `ancestors need descent into subtree`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(testTree, 32L)

        val invalidatedNode = treePointer.computeRead { treePointer.getNode(32L) }
        val ancestors = invalidatedNode.getAncestors(includeSelf = false)
        assertTrue { ancestors.all { invalidationTree.needsDescentIntoSubtree(it) } }
    }

    @Test
    fun `unchanged subtrees do not need descent or sync`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(testTree, 3L)

        val descendants = treePointer.getNode(3L).getDescendants(false)
        assertTrue { descendants.none { invalidationTree.needsSynchronization(it) } }
        assertTrue { descendants.none { invalidationTree.needsDescentIntoSubtree(it) } }
    }

    @Test
    fun `overlapping containment path`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(testTree, 3L)
        invalidationTree.invalidate(testTree, 311L)

        val ancestors = treePointer.getNode(3L).getAncestors(false)

        assertTrue { ancestors.all { invalidationTree.needsDescentIntoSubtree(it) } }
        assertTrue { ancestors.none { invalidationTree.needsSynchronization(it) } }

        assertTrue { invalidationTree.needsSynchronization(treePointer.getNode(3L)) }

        assertFalse { invalidationTree.needsSynchronization(treePointer.getNode(31L)) }
        assertTrue { invalidationTree.needsDescentIntoSubtree(treePointer.getNode(31L)) }

        assertTrue { invalidationTree.needsSynchronization(treePointer.getNode(311L)) }

        assertFalse { invalidationTree.needsSynchronization(treePointer.getNode(3111L)) }
        assertFalse { invalidationTree.needsDescentIntoSubtree(treePointer.getNode(3111L)) }
    }

    private fun getTestTreeData(): ITree {
        var tree = ModelFacade.newLocalTree()
        tree = tree.addNewChild(ITree.ROOT_ID, null, -1, 2L, null as IConceptReference?)
        tree = tree.addNewChild(ITree.ROOT_ID, null, -1, 3L, null as IConceptReference?)
        tree = tree.addNewChild(ITree.ROOT_ID, null, -1, 4L, null as IConceptReference?)

        tree = tree.addNewChild(3L, null, -1, 31L, null as IConceptReference?)
        tree = tree.addNewChild(3L, null, -1, 32L, null as IConceptReference?)
        tree = tree.addNewChild(3L, null, -1, 33L, null as IConceptReference?)

        tree = tree.addNewChild(31L, null, -1, 311L, null as IConceptReference?)
        tree = tree.addNewChild(311L, null, -1, 3111L, null as IConceptReference?)

        return tree
    }
}
