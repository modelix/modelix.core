package org.modelix.datastructures.btree


data class BTreeNode<T : Comparable<T>>(
    val keys: List<T> = listOf(),
    val children: List<BTreeNode<T>> = listOf(),
    val leaf: Boolean = true
) {
    fun search(key: T): BTreeNode<T>? {
        val index = keys.indexOfFirst { it >= key }.takeIf { it != -1 } ?: keys.size
        return when {
            index < keys.size && keys[index] == key -> this
            leaf -> null
            else -> children[index].search(key)
        }
    }

    fun insertNonFull(key: T, minDegree: Int): BTreeNode<T> {
        val index = keys.indexOfFirst { it > key }.takeIf { it != -1 } ?: keys.size

        return if (leaf) {
            // Insert key directly into the sorted list (immutably)
            copy(keys = (keys + key).sorted())
        } else {
            // Ensure we do not access a non-existent child
            if (index >= children.size) {
                throw IllegalStateException("Attempted to access child $index in a node with ${children.size} children.")
            }

            val (updatedChild, splitInfo) = children[index].insertWithSplit(key, minDegree)

            val newChildren = children.toMutableList().apply {
                this[index] = updatedChild.first() // Replace the modified child
            }

            if (splitInfo == null) {
                // No split happened, return updated node
                return copy(children = newChildren)
            } else {
                // A split happened, we need to insert the new key and right child
                val (middleKey, rightNode) = splitInfo

                return copy(
                    keys = keys.take(index) + middleKey + keys.drop(index),
                    children = newChildren.take(index + 1) + listOf(rightNode) + newChildren.drop(index + 1)
                )
            }
        }
    }

    fun insertWithSplit(key: T, minDegree: Int): Pair<List<BTreeNode<T>>, Pair<T, BTreeNode<T>>?> {
        val updatedNode = insertNonFull(key, minDegree)

        return if (updatedNode.keys.size < 2 * minDegree - 1) {
            listOf(updatedNode) to null
        } else {
            // Split node
            val mid = updatedNode.keys.size / 2
            val middleKey = updatedNode.keys[mid]

            val left = updatedNode.copy(
                keys = updatedNode.keys.take(mid),
                children = if (updatedNode.leaf) listOf() else updatedNode.children.take(mid + 1),
                leaf = updatedNode.leaf
            )

            val right = updatedNode.copy(
                keys = updatedNode.keys.drop(mid + 1),
                children = if (updatedNode.leaf) listOf() else updatedNode.children.drop(mid + 1),
                leaf = updatedNode.leaf
            )

            listOf(left, right) to (middleKey to right)
        }
    }
}

data class BTree<T : Comparable<T>>(val root: BTreeNode<T> = BTreeNode(), val minDegree: Int) {
    fun search(key: T): Boolean = root.search(key) != null

    fun insert(key: T): BTree<T> {
        val (updatedRootChildren, splitInfo) = root.insertWithSplit(key, minDegree)

        return if (splitInfo == null) {
            copy(root = updatedRootChildren.first())
        } else {
            // Root split, create a new root
            val (middleKey, rightChild) = splitInfo
            val newRoot = BTreeNode(
                keys = listOf(middleKey),
                children = listOf(updatedRootChildren.first(), rightChild),
                leaf = false
            )
            copy(root = newRoot)
        }
    }

    fun traverse(node: BTreeNode<T> = root, level: Int = 0) {
        println("Level $level: ${node.keys}")
        node.children.forEach { traverse(it, level + 1) }
    }
}

fun main() {
    var tree = BTree<Int>(minDegree = 2) // Minimum degree 2

    listOf(10, 20, 5, 6, 12, 30, 7, 17).forEach {
        tree = tree.insert(it) // Immutable update
    }

    println("Persistent B-Tree structure:")
    tree.traverse()

    val searchKey = 12
    println("Searching for $searchKey: ${tree.search(searchKey)}")
}
