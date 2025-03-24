package org.modelix.datastructures.btree

data class BTree<K : Comparable<K>, V>(
    val root: BTreeNode<K, V>,
) {
    constructor(minEntries: Int = 8) : this(BTreeNode(minEntries))

    fun put(key: K, value: V): BTree<K, V> = copy(root = root.put(key, value).createRoot().also { it.validate() })
    fun get(key: K): V? = root.get(key)
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot().also { it.validate() })
}

data class BTreeNode<K : Comparable<K>, V>(
    val config: BTreeConfig<K, V>,
    val entries: List<Pair<K, V>>,
    val children: List<BTreeNode<K, V>>,
) {
    constructor(minEntries: Int = 8) : this(BTreeConfig(minEntries = minEntries), emptyList(), emptyList())

    init {
        validate()
    }

    fun validate() {
        check(children.isEmpty() || children.size == entries.size + 1) {
            "entries: ${entries.size}, children expected: ${entries.size + 1}, children actual: ${children.size}"
        }
        for (child in children) {
            check((config.minEntries..config.maxEntries).contains(child.entries.size)) {
                "$child"
            }
            child.validate()
        }
        children.forEach { it.validate() }
    }

    inner class ChildrenRange(val range: IntRange) {
        constructor(index: Int) : this(index..index)
        val parent: BTreeNode<K, V> get() = this@BTreeNode
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
            ).checkSize()
        }

        fun extendLeft() = ChildrenRange((range.first - 1)..range.last)
        fun extendRight() = ChildrenRange(range.first..(range.last + 1))
    }

    private fun split(): UpdateResult.Overfill<K, V> {
        val medianIndex = entries.size / 2
        val medianValue = entries[medianIndex]

        val left = BTreeNode<K, V>(config, entries.take(medianIndex), children.take(medianIndex + 1))
        val right = BTreeNode<K, V>(config, entries.drop(medianIndex + 1), children.drop(medianIndex + 1))

        return UpdateResult.Overfill(left, right, medianValue)
    }

    fun mergeWith(centerEntry: Pair<K, V>, right: BTreeNode<K, V>): BTreeNode<K, V> {
        return copy(
            entries = entries + centerEntry + right.entries,
            children = children + right.children,
        )
    }

    fun checkSize(): UpdateResult<K, V> {
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
            insertEntry(key to value).checkSize()
        } else {
            val index = entries.binarySearch { it.first.compareTo(key) }
            if (index >= 0) {
                copy(entries = entries.take(index) + (key to value) + entries.drop(index + 1)).checkSize()
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

    fun remove(key: K): UpdateResult<K, V> {
        val index = entries.binarySearch { it.first.compareTo(key) }
        return if (index >= 0) {
            if (children.isEmpty()) {
                copy(entries = entries.take(index) + entries.drop(index + 1)).checkSize()
            } else {
                val childBefore = children[index]
                val childAfter = children[index + 1]
                if (childBefore.entries.size <= config.minEntries && childAfter.entries.size <= config.minEntries) {
                    childBefore.mergeWith(entries[index], childAfter)
                        .remove(key)
                        .apply(ChildrenRange(index..(index + 1)))
                } else if (childBefore.entries.size > childAfter.entries.size) {
                    val shiftedLeaf = childBefore.removeLastEntry()
                    ChildrenRange(index..(index + 1)).replaceWith(
                        shiftedLeaf.updatedNode,
                        shiftedLeaf.entry,
                        childAfter,
                    )
                } else {
                    val shiftedLeaf = childAfter.removeFirstEntry()
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

    private fun removeFirstEntry(): RemovedEntry<K, V> {
        return if (children.isEmpty()) {
            RemovedEntry(
                entries.first(),
                copy(entries = entries.drop(1)),
            )
        } else {
            if (children.first().entries.size <= config.minEntries) {
                val mergedChild = children[0].mergeWith(entries[0], children[1])
                val withRemovedEntry = mergedChild.removeFirstEntry()
                withRemovedEntry.copy(
                    updatedNode = withRemovedEntry.updatedNode.checkSize().apply(ChildrenRange(0..1)).expectCompletion(),
                )
            } else {
                children.first().removeFirstEntry().let {
                    RemovedEntry(it.entry, ChildrenRange(0).replaceWith(it.updatedNode))
                }
            }
        }
    }

    private fun removeLastEntry(): RemovedEntry<K, V> {
        return if (children.isEmpty()) {
            RemovedEntry(
                entries.last(),
                copy(entries = entries.dropLast(1)),
            )
        } else {
            if (children.last().entries.size <= config.minEntries) {
                val mergedChild = children[children.lastIndex - 1].mergeWith(entries.last(), children.last())
                val withRemovedEntry = mergedChild.removeLastEntry()
                withRemovedEntry.copy(
                    updatedNode = withRemovedEntry.updatedNode.checkSize().apply(ChildrenRange((children.lastIndex - 1)..children.lastIndex)).expectCompletion(),
                )
            } else {
                children.last().removeLastEntry().let {
                    RemovedEntry(it.entry, ChildrenRange(children.lastIndex).replaceWith(it.updatedNode))
                }
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

data class RemovedEntry<K : Comparable<K>, V>(val entry: Pair<K, V>, val updatedNode: BTreeNode<K, V>)

sealed class UpdateResult<K : Comparable<K>, V> {
    abstract fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V>
    abstract fun createRoot(): BTreeNode<K, V>
    abstract fun expectCompletion(): BTreeNode<K, V>

    class Complete<K : Comparable<K>, V>(val newNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.replaceWith(newNode).checkSize()
        }

        override fun createRoot(): BTreeNode<K, V> {
            return newNode
        }

        override fun expectCompletion(): BTreeNode<K, V> = newNode
    }
    class Overfill<K : Comparable<K>, V>(val left: BTreeNode<K, V>, val right: BTreeNode<K, V>, val medianEntry: Pair<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.replaceWith(left, medianEntry, right)
        }

        override fun createRoot(): BTreeNode<K, V> {
            return BTreeNode(left.config, listOf(medianEntry), listOf(left, right))
        }
        override fun expectCompletion(): BTreeNode<K, V> = throw IllegalStateException("Not complete: $this")
    }
    class Underfill<K : Comparable<K>, V>(val newNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            val prevSibling = toReplace.parent.children.getOrNull(toReplace.range.first - 1)
            val nextSibling = toReplace.parent.children.getOrNull(toReplace.range.last + 1)

            return if (prevSibling == null) {
                if (nextSibling == null) {
                    TODO("Can this happen?")
                } else {
                    newNode.mergeWith(toReplace.parent.entries[toReplace.range.last], nextSibling)
                        .checkSize()
                        .apply(toReplace.extendRight())
                }
            } else {
                if (nextSibling == null) {
                    prevSibling.mergeWith(toReplace.parent.entries[toReplace.range.first - 1], newNode)
                        .checkSize()
                        .apply(toReplace.extendLeft())
                } else {
                    if (prevSibling.entries.size > nextSibling.entries.size) {
                        newNode.mergeWith(toReplace.parent.entries[toReplace.range.last], nextSibling)
                            .checkSize()
                            .apply(toReplace.extendRight())
                    } else {
                        prevSibling.mergeWith(toReplace.parent.entries[toReplace.range.first - 1], newNode)
                            .checkSize()
                            .apply(toReplace.extendLeft())
                    }
                }
            }
        }

        override fun createRoot(): BTreeNode<K, V> {
            // root node is allowed to be smaller
            return newNode
        }

        override fun expectCompletion(): BTreeNode<K, V> = throw IllegalStateException("Not complete: $this")
    }
    class NothingChanged<K : Comparable<K>, V>(val oldNode: BTreeNode<K, V>) : UpdateResult<K, V>() {
        override fun apply(toReplace: BTreeNode<K, V>.ChildrenRange): UpdateResult<K, V> {
            return toReplace.parent.checkSize()
        }

        override fun createRoot(): BTreeNode<K, V> {
            return oldNode
        }

        override fun expectCompletion(): BTreeNode<K, V> = oldNode
    }
}

data class BTreeConfig<K : Comparable<K>, V>(val minEntries: Int = 2) {
    val minChildren = minEntries + 1
    val maxEntries = 2 * minEntries
    val maxChildren = maxEntries + 1
    val emptyNode = BTreeNode(this, emptyList(), emptyList())
}
