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
}

data class BTree<K : Comparable<K>, V>(val root: BTreeNode<K, V> = BTreeNode(), val minDegree: Int = 2) {

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

    fun update(key: K, value: V): BTree<K, V> {
        // We simply insert the key-value pair again, updating the value if the key exists
        return insert(key, value)
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

    println("Persistent B-Tree structure:")
    tree.traverse()

    val searchKey = 12
    println("Searching for $searchKey: ${tree.search(searchKey)}")

    // Update an existing key
    tree = tree.update(12, "Updated Twelve")
    println("Updated value for 12: ${tree.search(12)}")
}
