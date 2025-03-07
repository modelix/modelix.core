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

        invalidationTree.invalidate(treePointer.getNode(32L).asReadableNode())

        val invalidatedNode = treePointer.computeRead { treePointer.getNode(32L).asReadableNode() }
        assertTrue { invalidationTree.needsSynchronization(invalidatedNode) }
    }

    @Test
    fun `ancestors need descent into subtree`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(treePointer.getNode(32L).asReadableNode())

        val invalidatedNode = treePointer.computeRead { treePointer.getNode(32L) }
        val ancestors = invalidatedNode.getAncestors(includeSelf = false)
        assertTrue { ancestors.all { invalidationTree.needsDescentIntoSubtree(it.asReadableNode()) } }
    }

    @Test
    fun `unchanged subtrees do not need descent or sync`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(treePointer.getNode(3L).asReadableNode())

        val descendants = treePointer.getNode(3L).getDescendants(false)
        assertTrue { descendants.none { invalidationTree.needsSynchronization(it.asReadableNode()) } }
        assertTrue { descendants.none { invalidationTree.needsDescentIntoSubtree(it.asReadableNode()) } }
    }

    @Test
    fun `overlapping containment path`() {
        val invalidationTree = InvalidationTree(100)
        val testTree = getTestTreeData()
        val treePointer = TreePointer(testTree)

        invalidationTree.invalidate(treePointer.getNode(3L).asReadableNode())
        invalidationTree.invalidate(treePointer.getNode(311L).asReadableNode())

        val ancestors = treePointer.getNode(3L).getAncestors(false)

        assertTrue { ancestors.all { invalidationTree.needsDescentIntoSubtree(it.asReadableNode()) } }
        assertTrue { ancestors.none { invalidationTree.needsSynchronization(it.asReadableNode()) } }

        assertTrue { invalidationTree.needsSynchronization(treePointer.getNode(3L).asReadableNode()) }
        assertTrue { invalidationTree.needsDescentIntoSubtree(treePointer.getNode(3L).asReadableNode()) }

        assertFalse { invalidationTree.needsSynchronization(treePointer.getNode(31L).asReadableNode()) }
        assertTrue { invalidationTree.needsDescentIntoSubtree(treePointer.getNode(31L).asReadableNode()) }

        assertTrue { invalidationTree.needsSynchronization(treePointer.getNode(311L).asReadableNode()) }

        assertFalse { invalidationTree.needsSynchronization(treePointer.getNode(3111L).asReadableNode()) }
        assertFalse { invalidationTree.needsDescentIntoSubtree(treePointer.getNode(3111L).asReadableNode()) }
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
