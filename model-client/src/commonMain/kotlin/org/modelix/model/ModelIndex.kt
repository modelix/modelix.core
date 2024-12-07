package org.modelix.model

import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx

class ModelIndex private constructor(val tree: ITree, val propertyRole: String) {
    companion object {
        fun fromTree(tree: ITree, propertyRole: String): ModelIndex {
            val index = ModelIndex(tree, propertyRole)
            index.loadAll(ITree.ROOT_ID)
            return index
        }

        fun incremental(oldIndex: ModelIndex, newTree: ITree): ModelIndex {
            val oldTree = oldIndex.tree
            if (oldTree == newTree) return oldIndex
            val index = ModelIndex(newTree, oldIndex.propertyRole)
            index.nodeMap.putAll(oldIndex.nodeMap.map { it.key to HashSet(it.value) })
            newTree.visitChanges(
                oldTree,
                object : ITreeChangeVisitorEx {
                    override fun childrenChanged(nodeId: Long, role: String?) {}

                    override fun propertyChanged(nodeId: Long, role: String) {
                        if (role == index.propertyRole) {
                            index.nodeMap[oldIndex.readKey(nodeId)]?.remove(nodeId)
                            index.loadNode(nodeId)
                        }
                    }

                    override fun referenceChanged(nodeId: Long, role: String) {}
                    override fun containmentChanged(nodeId: Long) {}
                    override fun conceptChanged(nodeId: Long) {}

                    override fun nodeRemoved(nodeId: Long) {
                        val key = oldIndex.readKey(nodeId)
                        index.nodeMap[key]?.remove(nodeId)
                    }

                    override fun nodeAdded(nodeId: Long) {
                        index.loadNode(nodeId)
                    }
                },
            )

            return index
        }

        fun get(transaction: ITransaction, propertyRole: String): ModelIndex {
            val userObjectKey = "index-$propertyRole"
            var index: ModelIndex? = transaction.getUserObject(userObjectKey) as? ModelIndex
            if (index == null) {
                index = fromTree(transaction.tree, propertyRole)
                transaction.putUserObject(userObjectKey, index)
            } else {
                if (index.tree != transaction.tree) {
                    index = incremental(index, transaction.tree)
                    transaction.putUserObject(userObjectKey, index)
                }
            }
            return index
        }
    }

    private val nodeMap: MutableMap<String?, MutableSet<Long>> = HashMap()

    private fun loadAll(nodeId: Long) {
        loadNode(nodeId)
        tree.getAllChildren(nodeId).forEach { loadAll(it) }
    }

    private fun loadNode(nodeId: Long) {
        val key = readKey(nodeId)
        nodeMap.getOrPut(key) { HashSet(1) }.add(nodeId)
    }

    private fun readKey(nodeId: Long): String? = tree.getProperty(nodeId, propertyRole)

    fun find(propertyValue: String?): Set<Long> {
        return nodeMap[propertyValue] ?: emptySet()
    }
}
