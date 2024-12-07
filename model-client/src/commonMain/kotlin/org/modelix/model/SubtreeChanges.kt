package org.modelix.model

import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.lazy.IBulkTree
import kotlin.jvm.Synchronized

class SubtreeChanges(val cacheSize: Int) {
    private val newTreeOrder = ArrayDeque<ITree>(cacheSize)
    private val cache = HashMap<ITree, HashMap<ITree, Data>>()

    @Synchronized
    fun getAffectedSubtrees(oldTree: ITree, newTree: ITree): Set<Long> {
        val old2data = cache.getOrPut(newTree) {
            newTreeOrder.addLast(newTree)
            HashMap()
        }
        val result = old2data.getOrPut(oldTree) { Data(oldTree, newTree) }.affectedSubtrees
        if (newTreeOrder.size > cacheSize) {
            cache.remove(newTreeOrder.removeFirst())
        }
        return result
    }

    private inner class Data(val oldTree: ITree, val newTree: ITree) {
        val affectedSubtrees: Set<Long>
        init {
            require(oldTree is IBulkTree) { "Type of tree is not supported: $oldTree" }
            val affectedNodes = HashSet<Long>()
            newTree.visitChanges(
                oldTree,
                object : ITreeChangeVisitor {

                    override fun childrenChanged(nodeId: Long, role: String?) {
                        affectedNodes += nodeId
                    }

                    override fun containmentChanged(nodeId: Long) {
                        // there is always a corresponding childrenChanged event for this
                    }

                    override fun conceptChanged(nodeId: Long) {
                        affectedNodes += nodeId
                    }

                    override fun propertyChanged(nodeId: Long, role: String) {
                        affectedNodes += nodeId
                    }

                    override fun referenceChanged(nodeId: Long, role: String) {
                        affectedNodes += nodeId
                    }
                },
            )
            affectedSubtrees = oldTree.getAncestors(affectedNodes, true)
        }
    }

    companion object {
        val INSTANCE = SubtreeChanges(10)
    }
}
