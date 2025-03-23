package org.modelix.datastructures.btree

data class BTreeNode<K : Comparable<K>, V>(
    val config: BTreeConfig<K, V>,
    val entries: List<Pair<K, V>>,
    val children: List<BTreeNode<K, V>>,
) {
    constructor(minEntries: Int = 2) : this(BTreeConfig(minEntries = minEntries), emptyList(), emptyList())

    init {
        require(children.isEmpty() || children.size == entries.size + 1) {
            "entries: ${entries.size}, children expected: ${entries.size + 1}, children actual: ${children.size}"
        }
    }

    inner class ChildrenRange(val range: IntRange) {
        constructor(index: Int) : this(index..index)
        val parent = this@BTreeNode
        fun replaceWith(newChild: BTreeNode<K, V>): BTreeNode<K, V> {
            return copy(
                entries = if (range.first < range.last) {
                    entries.take(range.first) + entries.drop(range.last)
                } else {
                    entries
                },
                children = children.take(range.first) + newChild + children.drop(range.last + 1),
            )
        }

        fun replaceWith(leftChild: BTreeNode<K, V>, centerEntry: Pair<K, V>, rightChild: BTreeNode<K, V>): UpdateResult<K, V> {
            return copy(
                entries = entries.take(range.first) + centerEntry + entries.drop(range.last),
                children = children.take(range.first) + leftChild + rightChild + children.drop(range.last + 1),
            ).splitIfNecessary()
        }
    }

    private fun split(): UpdateResult.Overfill<K, V> {
        val medianIndex = entries.size / 2
        val medianValue = entries[medianIndex]

        val left = BTreeNode<K, V>(config, entries.take(medianIndex), children.take(medianIndex + 1))
        val right = BTreeNode<K, V>(config, entries.drop(medianIndex + 1), children.drop(medianIndex + 1))

        return UpdateResult.Overfill(left, right, medianValue)
    }

    fun mergeWith(centerEntry: Pair<K, V>, right: BTreeNode<K, V>): UpdateResult<K, V> {
        return copy(
            entries = entries + centerEntry + right.entries,
            children = children + right.children,
        ).splitIfNecessary()
    }

    fun splitIfNecessary(): UpdateResult<K, V> {
        return if (entries.size > config.maxEntries) {
            split()
        } else if (entries.size < config.minEntries) {
            UpdateResult.Underfill(this)
        } else {
            UpdateResult.Complete(this)
        }
    }

    fun put(key: K, value: V): UpdateResult<K, V> {
        return if (children.isEmpty()) {
            insertEntry(key to value).splitIfNecessary()
        } else {
            val index = entries.binarySearch { it.first.compareTo(key) }
            if (index >= 0) {
                copy(entries = entries.take(index) + (key to value) + entries.drop(index + 1)).splitIfNecessary()
            } else {
                val insertionIndex = (-index) - 1
                val childUpdateResult = children[insertionIndex].put(key, value)
                childUpdateResult.apply(ChildrenRange(insertionIndex))
            }
        }
    }

    fun get(key: K): V? {
        val index = entries.binarySearch { it.first.compareTo(key) }
        return if (index >= 0) {
            entries[index].second
        } else {
            val insertionIndex = (-index) - 1
            children.getOrNull(insertionIndex)?.get(key)
        }
    }

    /**
     * @return null if nothing changed
     */
    fun remove(key: K): UpdateResult<K, V> {
        val index = entries.binarySearch { it.first.compareTo(key) }
        return if (index >= 0) {
            if (children.isEmpty()) {
                copy(entries = entries.take(index) + entries.drop(index + 1)).splitIfNecessary()
            } else {
                val childBefore = children[index]
                val childAfter = children[index + 1]
                if (childBefore.entries.size > childAfter.entries.size) {
                    val shiftedLeaf = childBefore.removeLastLeaf()
                    ChildrenRange(index..(index + 1)).replaceWith(
                        shiftedLeaf.updatedNode,
                        shiftedLeaf.entry,
                        childAfter,
                    )
                } else {
                    val shiftedLeaf = childAfter.removeFirstLeaf()
                    ChildrenRange(index..(index + 1)).replaceWith(
                        childBefore,
                        shiftedLeaf.entry,
                        shiftedLeaf.updatedNode,
                    )
                }
            }
        } else {
            val insertionIndex = (-index) - 1
            val newChild = children.getOrNull(insertionIndex)?.remove(key) ?: return UpdateResult.NothingChanged(this)
            newChild.apply(ChildrenRange(insertionIndex))
        }
    }

    private fun removeFirstLeaf(): RemovedEntry<K, V> {
        return if (children.isEmpty()) {
            RemovedEntry(
                entries.first(),
                copy(entries = entries.drop(1)),
            )
        } else {
            children.first().removeFirstLeaf().let {
                RemovedEntry(it.entry, ChildrenRange(0).replaceWith(it.updatedNode))
            }
        }
    }

    private fun removeLastLeaf(): RemovedEntry<K, V> {
        return if (children.isEmpty()) {
            RemovedEntry(
                entries.last(),
                copy(entries = entries.dropLast(1)),
            )
        } else {
            children.last().removeLastLeaf().let {
                RemovedEntry(it.entry, ChildrenRange(children.lastIndex).replaceWith(it.updatedNode))
            }
        }
    }

    private fun insertEntry(newEntry: Pair<K, V>): BTreeNode<K, V> {
        val index = entries.binarySearch { it.first.compareTo(newEntry.first) }
        if (index >= 0) {
            return copy(
                entries = entries.take(index) + newEntry + entries.drop(index + 1),
            )
        } else {
            val insertionIndex = if (index >= 0) index else (-index) - 1
            return copy(
                entries = entries.take(insertionIndex) + newEntry + entries.drop(insertionIndex),
            )
        }
    }
}

class RemovedEntry<K : Comparable<K>, V>(val entry: Pair<K, V>, val updatedNode: BTreeNode<K, V>)

sealed class UpdateResult<K : Comparable<K>, V> {
    abstract fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V>
    abstract fun createRoot(): BTreeNode<K, V>

    class Complete<K : Comparable<K>, V>(val newNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.replaceWith(newNode).splitIfNecessary()
        }

        override fun createRoot(): BTreeNode<K, V> {
            return newNode
        }
    }
    class Overfill<K : Comparable<K>, V>(val left: BTreeNode<K, V>, val right: BTreeNode<K, V>, val medianEntry: Pair<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.replaceWith(left, medianEntry, right)
        }

        override fun createRoot(): BTreeNode<K, V> {
            return BTreeNode(left.config, listOf(medianEntry), listOf(left, right))
        }
    }
    class Underfill<K : Comparable<K>, V>(val newNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            TODO("Not yet implemented")
        }

        override fun createRoot(): BTreeNode<K, V> {
            // root node is allowed to be smaller
            return newNode
        }
    }
    class NothingChanged<K : Comparable<K>, V>(val oldNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.parent.splitIfNecessary()
        }

        override fun createRoot(): BTreeNode<K, V> {
            return oldNode
        }
    }
}

data class BTreeConfig<K : Comparable<K>, V>(val minEntries: Int = 2) {
    val minChildren = minEntries + 1
    val maxEntries = 2 * minEntries
    val maxChildren = maxEntries + 1
    val emptyNode = BTreeNode(this, emptyList(), emptyList())
}
