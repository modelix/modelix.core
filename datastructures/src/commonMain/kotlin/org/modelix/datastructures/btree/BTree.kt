package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.serialization.SerializationSeparators

data class BTree<K, V>(val root: BTreeNode<K, V>) {
    constructor(config: BTreeConfig<K, V>) : this(BTreeNode(config, emptyList(), emptyList()))

    fun validate() {
        root.validate(true)
        check(root.getEntries().map { it.key }.toSet().size == root.getEntries().map { it.key }.count()) {
            "duplicate entries: $root"
        }
        check(root.getEntries().map { it.key }.toList().sortedWith(root.config.keyComparator) == root.getEntries().map { it.key }.toList()) {
            "not sorted: $this"
        }
    }
    fun put(key: K, value: V): BTree<K, V> = copy(root = root.put(key, value).createRoot())
    fun get(key: K): V? = root.get(key)
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot())
}

data class BTreeNode<K, V>(
    val config: BTreeConfig<K, V>,
    val entries: List<Entry<K, V>>,
    val children: List<BTreeNode<K, V>>,
) : IObjectData {

    private operator fun K.compareTo(other: K): Int = config.keyComparator.compare(this, other)

    fun validate(isRoot: Boolean) {
        check(children.isEmpty() || children.size == entries.size + 1) {
            "entries: ${entries.size}, children expected: ${entries.size + 1}, children actual: ${children.size}"
        }
        check(entries.size == entries.map { it.key }.toSet().size) {
            "duplicate entries: $entries"
        }
        check(entries.map { it.key }.sortedWith(config.keyComparator) == entries.map { it.key }) {
            "entries not sorted: $entries"
        }
        check(entries.size <= config.maxEntries) {
            "overfilled: $this"
        }
        if (!isRoot) {
            check(entries.size >= config.minEntries) {
                "underfilled: $this"
            }
        }
        if (children.isNotEmpty()) {
            for ((index, entry) in entries.withIndex()) {
                check(children[index].getLastEntry().key < entry.key) {
                    "not sorted: $this"
                }
                check(children[index + 1].getFirstEntry().key > entry.key) {
                    "not sorted: $this"
                }
            }
        }
        for (child in children) {
            child.validate(false)
        }
    }

    fun getEntries(): Sequence<Entry<K, V>> {
        return if (children.isEmpty()) {
            entries.asSequence()
        } else {
            (0 until (entries.size + children.size)).asSequence().flatMap { i ->
                if (i % 2 == 0) children[i / 2].getEntries() else sequenceOf(entries[i / 2])
            }
        }
    }

    private fun childrenRange(index: Int) = ChildrenRange(this, index)
    private fun childrenRange(first: Int, last: Int) = ChildrenRange(this, first..last)

    fun size() = entries.size
    fun hasMinSize() = entries.size <= config.minEntries
    fun hasMaxSize() = entries.size >= config.maxEntries

    private fun split(): Replacement.Splitted<K, V> {
        val medianIndex = entries.size / 2
        val medianValue = entries[medianIndex]

        val left = BTreeNode<K, V>(config, entries.take(medianIndex), children.take(medianIndex + 1))
        val right = BTreeNode<K, V>(config, entries.drop(medianIndex + 1), children.drop(medianIndex + 1))

        return Replacement.Splitted(left, right, medianValue)
    }

    fun mergeWith(centerEntry: Entry<K, V>, right: BTreeNode<K, V>): BTreeNode<K, V> {
        return copy(
            entries = entries + centerEntry + right.entries,
            children = children + right.children,
        )
    }

    fun splitIfNecessary(): Replacement<K, V> {
        return if (entries.size > config.maxEntries) {
            split()
        } else {
            Replacement.Single(this)
        }
    }

    fun put(key: K, value: V): Replacement<K, V> {
        return if (children.isEmpty()) {
            insertEntry(Entry(key, value)).splitIfNecessary()
        } else {
            val index = entries.binarySearch { it.key.compareTo(key) }
            if (index >= 0) {
                copy(entries = entries.take(index) + Entry(key, value) + entries.drop(index + 1)).splitIfNecessary()
            } else {
                val insertionIndex = (-index) - 1
                children[insertionIndex].put(key, value).apply(childrenRange(insertionIndex)).splitIfNecessary()
            }
        }
    }

    fun get(key: K): V? {
        val index = entries.binarySearch { it.key.compareTo(key) }
        return if (index >= 0) {
            entries[index].value
        } else {
            val insertionIndex = (-index) - 1
            children.getOrNull(insertionIndex)?.get(key)
        }
    }

    fun remove(key: K): Replacement<K, V> {
        val index = entries.binarySearch { it.key.compareTo(key) }
        return if (index >= 0) {
            if (children.isEmpty()) {
                Replacement.Single(copy(entries = entries.take(index) + entries.drop(index + 1)))
            } else {
                val siblingsRange = childrenRange(index, index + 1)
                val childBefore = children[index]
                val childAfter = children[index + 1]
                if (childBefore.hasMinSize() && childAfter.hasMinSize()) {
                    childBefore.mergeWith(entries[index], childAfter)
                        .remove(key)
                        .apply(siblingsRange)
                } else if (childBefore.size() > childAfter.size()) {
                    val shiftedEntry = childBefore.removeLastEntry()
                    siblingsRange.replaceWith(
                        shiftedEntry.updatedNode.expectSingle(),
                        shiftedEntry.entry,
                        childAfter,
                    )
                } else {
                    val shiftedEntry = childAfter.removeFirstEntry()
                    siblingsRange.replaceWith(
                        childBefore,
                        shiftedEntry.entry,
                        shiftedEntry.updatedNode.expectSingle(),
                    )
                }
            }
        } else {
            if (children.isEmpty()) {
                Replacement.Single(this)
            } else {
                val insertionIndex = (-index) - 1
                childrenRange(insertionIndex)
                    .mergeWithSiblingBeforeRemoveIfNecessary()
                    .removeEntry(key)
                    .splitIfNecessary()
                    .applyReplacement()
            }
        }
    }

    fun getFirstEntry(): BTreeNode.Entry<K, V> {
        return if (children.isEmpty()) {
            entries.first()
        } else {
            children.first().getFirstEntry()
        }
    }

    fun getLastEntry(): BTreeNode.Entry<K, V> {
        return if (children.isEmpty()) {
            entries.first()
        } else {
            children.first().getFirstEntry()
        }
    }

    fun removeFirstEntry(): RemovedEntry<K, V> = removeFirstOrLastEntry(true)

    fun removeLastEntry(): RemovedEntry<K, V> = removeFirstOrLastEntry(false)

    fun removeFirstOrLastEntry(first: Boolean): RemovedEntry<K, V> {
        check(size() > config.minEntries)
        return if (children.isEmpty()) {
            RemovedEntry(
                if (first) entries.first() else entries.last(),
                Replacement.Single(
                    copy(
                        entries = if (first) entries.drop(1) else entries.dropLast(1),
                    ),
                ),
            )
        } else {
            childrenRange(if (first) 0 else children.lastIndex)
                .mergeWithSiblingBeforeRemoveIfNecessary()
                .let { if (first) it.removeFirstEntry() else it.removeLastEntry() }
                .splitIfNecessary()
        }
    }

    private fun insertEntry(newEntry: Entry<K, V>): BTreeNode<K, V> {
        val index = entries.binarySearch { it.key.compareTo(newEntry.key) }
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

    override fun getDeserializer(): IObjectDeserializer<*> {
        return config.nodeDeserializer
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        //return children
        TODO()
    }

    override fun serialize(): String {
        return entries.joinToString(SerializationSeparators.LEVEL2) {
            config.keySerializer(it.key) + SerializationSeparators.MAPPING + config.valueSerializer(it.value)
        } + SerializationSeparators.LEVEL1 + children.joinToString(SerializationSeparators.LEVEL2) {
            //it.getHashString()
            TODO()
        }
    }

    class Deserializer<K, V>(val config: BTreeConfig<K, V>) : IObjectDeserializer<BTreeNode<K, V>> {
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): BTreeNode<K, V> {
            val parts = serialized.split(SerializationSeparators.LEVEL1)
            val entries = parts[0].split(SerializationSeparators.LEVEL2).map {
                val entryParts = it.split(SerializationSeparators.MAPPING, limit = 2)
                Entry(config.keyDeserializer(entryParts[0]), config.valueDeserializer(entryParts[1]))
            }
            val children = parts[1].split(SerializationSeparators.LEVEL2)
                .map { referenceFactory.fromHashString(it, this) }
            //return BTreeNode(config, entries, children)
            TODO()
        }
    }

    class Entry<K, V>(val key: K, val value: V) {
        override fun toString(): String {
            return "$key -> $value"
        }
    }
}

data class ChildrenRange<K, V>(val parent: BTreeNode<K, V>, val range: IntRange) {
    constructor(parent: BTreeNode<K, V>, index: Int) : this(parent, index..index)

    val config: BTreeConfig<K, V> get() = parent.config

    fun replaceWith(newChild: BTreeNode<K, V>): ChildrenRange<K, V> {
        return copy(
            parent = parent.copy(
                entries = if (range.first < range.last) {
                    parent.entries.take(range.first) + parent.entries.drop(range.last)
                } else {
                    parent.entries
                },
                children = parent.children.take(range.first) + newChild + parent.children.drop(range.last + 1),
            ),
        )
    }

    fun replaceWith(leftChild: BTreeNode<K, V>, centerEntry: BTreeNode.Entry<K, V>, rightChild: BTreeNode<K, V>): Replacement<K, V> {
        return parent.copy(
            entries = parent.entries.take(range.first) + centerEntry + parent.entries.drop(range.last),
            children = parent.children.take(range.first) + leftChild + rightChild + parent.children.drop(range.last + 1),
        ).splitIfNecessary()
    }

    fun merge(): PendingReplacement<K, V> {
        return when (range.size()) {
            1 -> PendingReplacement(this, Replacement.Single(firstInRange()))
            2 -> PendingReplacement(this, Replacement.Single(firstInRange().mergeWith(firstCenterEntry(), lastInRange())))
            else -> error("range spans across more than two children")
        }
    }

    fun mergeWithSiblingBeforeRemoveIfNecessary(): PendingReplacement<K, V> {
        check(range.size() == 1)
        return if (firstInRange().size() <= config.minEntries) {
            extendToSmaller().merge()
        } else {
            PendingReplacement(this, Replacement.Single(firstInRange()))
        }
    }

    fun firstInRange() = parent.children[range.first]
    fun lastInRange() = parent.children[range.last]
    fun firstCenterEntry() = parent.entries[range.first]
    fun size() = range.size()

    fun extendToSmaller(): ChildrenRange<K, V> {
        return if (range.first <= 0) {
            extendRight()
        } else if (range.last >= parent.children.lastIndex) {
            extendLeft()
        } else if (parent.children[range.first - 1].entries.size < parent.children[range.last + 1].entries.size) {
            extendLeft()
        } else {
            extendRight()
        }
    }
    fun extendLeft() = ChildrenRange(parent, (range.first - 1)..range.last)
    fun extendRight() = ChildrenRange(parent, range.first..(range.last + 1))
}

class PendingReplacement<K, V>(val childrenRange: ChildrenRange<K, V>, val replacement: Replacement<K, V>) {
    fun removeEntry(key: K): PendingReplacement<K, V> {
        return PendingReplacement(childrenRange, replacement.removeEntry(key).splitIfNecessary())
    }
    fun removeLastEntry(): RemovedEntry<K, V> {
        return replacement.removeLastEntry().let {
            it.copy(updatedNode = it.updatedNode.splitIfNecessary().apply(childrenRange))
        }
    }
    fun removeFirstEntry(): RemovedEntry<K, V> {
        return replacement.removeFirstEntry().let {
            it.copy(updatedNode = it.updatedNode.splitIfNecessary().apply(childrenRange))
        }
    }
    fun splitIfNecessary(): PendingReplacement<K, V> {
        return PendingReplacement(childrenRange, replacement.splitIfNecessary())
    }

    /**
     * Replaces the children and returns a replacement for the parent node.
     */
    fun applyReplacement(): Replacement<K, V> = replacement.apply(childrenRange)
}

data class RemovedEntry<K, V>(val entry: BTreeNode.Entry<K, V>, val updatedNode: Replacement<K, V>) {
    fun splitIfNecessary(): RemovedEntry<K, V> {
        return copy(updatedNode = updatedNode.splitIfNecessary())
    }
}

sealed class Replacement<K, V> {
    /**
     * Replaces the children and returns a replacement for the parent node
     */
    abstract fun apply(toReplace: ChildrenRange<K, V>): Replacement<K, V>
    abstract fun createRoot(): BTreeNode<K, V>
    abstract fun expectSingle(): BTreeNode<K, V>
    abstract fun removeEntry(key: K): Replacement<K, V>
    abstract fun removeFirstEntry(): RemovedEntry<K, V>
    abstract fun removeLastEntry(): RemovedEntry<K, V>
    abstract fun splitIfNecessary(): Replacement<K, V>

    class Single<K, V>(val newNode: BTreeNode<K, V>) : Replacement<K, V>() {
        override fun apply(toReplace: ChildrenRange<K, V>): Replacement<K, V> {
            check(newNode.size() <= newNode.config.maxEntries)
            return if (toReplace.size() == 1 && toReplace.firstInRange() === newNode) {
                Single(toReplace.parent) // nothing changed
            } else {
                toReplace.replaceWith(newNode).parent.splitIfNecessary()
            }
        }

        override fun createRoot(): BTreeNode<K, V> {
            return newNode
        }

        override fun expectSingle(): BTreeNode<K, V> = newNode
        override fun removeEntry(key: K): Replacement<K, V> = newNode.remove(key)
        override fun removeFirstEntry(): RemovedEntry<K, V> = newNode.removeFirstEntry()
        override fun removeLastEntry(): RemovedEntry<K, V> = newNode.removeLastEntry()
        override fun splitIfNecessary(): Replacement<K, V> = newNode.splitIfNecessary()
    }
    class Splitted<K, V>(
        val left: BTreeNode<K, V>,
        val right: BTreeNode<K, V>,
        val medianEntry: BTreeNode.Entry<K, V>,
    ) : Replacement<K, V>() {
        override fun apply(toReplace: ChildrenRange<K, V>): Replacement<K, V> {
            return toReplace.replaceWith(left, medianEntry, right)
        }

        override fun createRoot(): BTreeNode<K, V> {
            return BTreeNode(left.config, listOf(medianEntry), listOf(left, right))
        }
        override fun expectSingle(): BTreeNode<K, V> = throw IllegalStateException("Single node expected: $this")
        override fun splitIfNecessary(): Replacement<K, V> = this
        override fun removeEntry(key: K): Replacement<K, V> = error("unexpected")
        override fun removeFirstEntry(): RemovedEntry<K, V> = error("unexpected")
        override fun removeLastEntry(): RemovedEntry<K, V> = error("unexpected")
    }
}

private fun IntRange.size() = (last - first + 1)
