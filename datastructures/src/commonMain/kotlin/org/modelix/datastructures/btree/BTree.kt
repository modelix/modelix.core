package org.modelix.datastructures.btree

data class BTreeNode<K : Comparable<K>, V>(
    val entries: List<Pair<K, V>> = listOf(),
    val children: List<BTreeNode<K, V>> = listOf(),
    val leaf: Boolean = true
) {

    fun search(key: K): V? {
        val index = entries.indexOfFirst { it.first >= key }.takeIf { it != -1 } ?: entries.size
        return when {
            index < entries.size && entries[index].first == key -> entries[index].second
            leaf -> null
            else -> children[index].search(key)
        }
    }

    fun insertNonFull(key: K, value: V, minDegree: Int): BTreeNode<K, V> {
        val index = entries.indexOfFirst { it.first > key }.takeIf { it != -1 } ?: entries.size

        return if (leaf) {
            // Insert key-value pair into the sorted list (immutably)
            copy(entries = (entries + Pair(key, value)).sortedBy { it.first })
        } else {
            // Ensure we do not access a non-existent child
            if (index >= children.size) {
                throw IllegalStateException("Attempted to access child $index in a node with ${children.size} children.")
            }

            val (updatedChild, splitInfo) = children[index].insertWithSplit(key, value, minDegree)

            val newChildren = children.toMutableList().apply {
                this[index] = updatedChild.first() // Replace the modified child
            }

            if (splitInfo == null) {
                // No split happened, return updated node
                return copy(children = newChildren)
            } else {
                // A split happened, we need to insert the new key-value pair and right child
                val (middleKey, middleValue, rightNode) = splitInfo

                return copy(
                    entries = entries.take(index) + Pair(middleKey, middleValue) + entries.drop(index),
                    children = newChildren.take(index + 1) + listOf(rightNode) + newChildren.drop(index + 1)
                )
            }
        }
    }

    fun insertWithSplit(key: K, value: V, minDegree: Int): Pair<List<BTreeNode<K, V>>, Triple<K, V, BTreeNode<K, V>>?> {
        val updatedNode = insertNonFull(key, value, minDegree)

        return if (updatedNode.entries.size < 2 * minDegree - 1) {
            listOf(updatedNode) to null
        } else {
            // Split node
            val mid = updatedNode.entries.size / 2
            val middleKey = updatedNode.entries[mid].first
            val middleValue = updatedNode.entries[mid].second

            val left = updatedNode.copy(
                entries = updatedNode.entries.take(mid),
                children = if (updatedNode.leaf) listOf() else updatedNode.children.take(mid + 1),
                leaf = updatedNode.leaf
            )

            val right = updatedNode.copy(
                entries = updatedNode.entries.drop(mid + 1),
                children = if (updatedNode.leaf) listOf() else updatedNode.children.drop(mid + 1),
                leaf = updatedNode.leaf
            )

            listOf(left, right) to Triple(middleKey, middleValue, right)
        }
    }

    // Helper function to remove an entry from a non-empty leaf node
    fun removeFromLeaf(key: K): BTreeNode<K, V> {
        return copy(entries = entries.filterNot { it.first == key })
    }

    // Helper function to remove an entry from an internal node
    fun removeFromInternal(key: K, minDegree: Int): BTreeNode<K, V> {
        val index = entries.indexOfFirst { it.first >= key }
        if (index == -1 || entries[index].first != key) return this  // If the key is not found, return as is

        val (leftChild, rightChild) = children[index] to children[index + 1]
        val (successorKey, successorValue) = rightChild.entries.first()

        val newEntries = entries.take(index) + Pair(successorKey, successorValue) + entries.drop(index + 1)

        val newChildren = children.take(index) + listOf(leftChild) + children.drop(index + 2)

        return copy(entries = newEntries, children = newChildren)
    }
}

data class BTree<K : Comparable<K>, V>(val root: BTreeNode<K, V> = BTreeNode(), val minDegree: Int) {

    fun search(key: K): V? = root.search(key)

    fun insert(key: K, value: V): BTree<K, V> {
        val (updatedRootChildren, splitInfo) = root.insertWithSplit(key, value, minDegree)

        return if (splitInfo == null) {
            copy(root = updatedRootChildren.first())
        } else {
            // Root split, create a new root
            val (middleKey, middleValue, rightChild) = splitInfo
            val newRoot = BTreeNode(
                entries = listOf(Pair(middleKey, middleValue)),
                children = listOf(updatedRootChildren.first(), rightChild),
                leaf = false
            )
            copy(root = newRoot)
        }
    }

    fun remove(key: K): BTree<K, V> {
        val updatedRoot = root.remove(key, minDegree)
        return copy(root = updatedRoot)
    }

    // Remove entry starting from the root node
    private fun BTreeNode<K, V>.remove(key: K, minDegree: Int): BTreeNode<K, V> {
        if (this.entries.isEmpty()) return this  // If the node is empty, there's nothing to remove

        // Case 1: Key is in the leaf node, remove directly
        if (this.leaf) {
            return removeFromLeaf(key)
        }

        // Case 2: Key is in an internal node, remove by finding successor
        return removeFromInternal(key, minDegree)
    }

    fun traverse(node: BTreeNode<K, V> = root, level: Int = 0) {
        println("Level $level: ${node.entries}")
        node.children.forEach { traverse(it, level + 1) }
    }
}

fun main() {
    var tree = BTree<Int, String>(minDegree = 2) // Minimum degree 2

    tree = tree.insert(10, "Ten")
    tree = tree.insert(20, "Twenty")
    tree = tree.insert(5, "Five")
    tree = tree.insert(6, "Six")
    tree = tree.insert(12, "Twelve")
    tree = tree.insert(30, "Thirty")
    tree = tree.insert(7, "Seven")
    tree = tree.insert(17, "Seventeen")

    println("Persistent B-Tree structure before removal:")
    tree.traverse()

    tree = tree.remove(12)
    println("\nPersistent B-Tree structure after removing key 12:")
    tree.traverse()

    tree = tree.remove(6)
    println("\nPersistent B-Tree structure after removing key 6:")
    tree.traverse()
}

