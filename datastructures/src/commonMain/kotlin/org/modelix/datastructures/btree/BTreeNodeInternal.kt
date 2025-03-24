package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.serialization.SerializationSeparators
import kotlin.collections.plus
import kotlin.text.iterator

data class BTreeNodeInternal<K, V>(
    override val config: BTreeConfig<K, V>,
    val separatorKeys: List<K>,
    val children: List<BTreeNode<K, V>>,
) : BTreeNode<K, V>() {

    override fun validate(isRoot: Boolean) {
        check(children.isEmpty() || children.size == separatorKeys.size + 1) {
            "separators: ${separatorKeys.size}, children expected: ${separatorKeys.size + 1}, children actual: ${children.size}"
        }
        check(separatorKeys.size == separatorKeys.toSet().size) {
            "duplicate entries: $separatorKeys"
        }
        check(separatorKeys.sortedWith(config.keyComparator) == separatorKeys) {
            "separators not sorted: $separatorKeys"
        }
        check(children.size <= config.maxChildren) {
            "overfilled: $this"
        }
        if (!isRoot) {
            check(children.size >= config.minChildren) {
                "underfilled: $this"
            }
        }
        for ((index, separator) in separatorKeys.withIndex()) {
            check(children[index].getLastEntry().key < separator) {
                "not sorted: $this"
            }
            check(children[index + 1].getFirstEntry().key >= separator) {
                "not sorted: $this"
            }
        }
        for (child in children) {
            child.validate(false)
        }
    }

    override fun getEntries(): Sequence<BTreeEntry<K, V>> {
        return children.asSequence().flatMap { it.getEntries() }
    }

    private fun childrenRange(index: Int) = ChildrenRange(this, index)
    private fun childrenRange(first: Int, last: Int) = ChildrenRange(this, first..last)

    override fun size(): Int = children.size
    override fun hasMinSize() = children.size <= config.minChildren
    override fun hasMaxSize() = children.size >= config.maxChildren
    override fun isOverfilled() = children.size > config.maxChildren

    override fun split(): Replacement.Splitted<K, V> {
        val leftChildrenSize = (children.size + 1) / 2 // left gets one more if odd number of children
        val leftSeparatorsSize = leftChildrenSize - 1
        val rightSeparatorsSize = separatorKeys.size - leftSeparatorsSize - 1
        val separatorForParent = separatorKeys[leftSeparatorsSize]

        val left = BTreeNodeInternal<K, V>(
            config,
            separatorKeys.take(leftSeparatorsSize),
            children.take(leftChildrenSize),
        )
        val right = BTreeNodeInternal<K, V>(
            config,
            separatorKeys.takeLast(rightSeparatorsSize),
            children.drop(leftChildrenSize),
        )

        return Replacement.Splitted(left, separatorForParent, right)
    }

    /**
     * @param knownSeparator could be retrieved from [right], but to avoid unnecessary traversal it has to be provided
     */
    override fun mergeWithSibling(knownSeparator: K, right: BTreeNode<K, V>): BTreeNodeInternal<K, V> {
        right as BTreeNodeInternal<K, V>
        return copy(
            separatorKeys = separatorKeys + knownSeparator + right.separatorKeys,
            children = children + right.children,
        )
    }

    private fun childIndexForKey(key: K): Int {
        val index = separatorKeys.binarySearch { it.compareTo(key) }
        return if (index >= 0) index + 1 else (-index) - 1
    }

    override fun put(key: K, value: V): Replacement<K, V> {
        val index = separatorKeys.binarySearch { it.compareTo(key) }
        val childIndex = childIndexForKey(key)
        return children[childIndex].put(key, value).apply(childrenRange(childIndex)).splitIfNecessary()
    }

    override fun get(key: K): V? {
        val childIndex = childIndexForKey(key)
        return children[childIndex].get(key)
    }

    override fun remove(key: K): Replacement<K, V> {
        val childIndex = childIndexForKey(key)
        return childrenRange(childIndex)
            .mergeWithSiblingBeforeRemoveIfNecessary()
            .removeEntry(key)
            .splitIfNecessary()
            .applyReplacement()
    }

    override fun getFirstEntry(): BTreeEntry<K, V> {
        return children.first().getFirstEntry()
    }

    override fun getLastEntry(): BTreeEntry<K, V> {
        return children.last().getLastEntry()
    }

    override fun removeFirstOrLastEntry(first: Boolean): RemovedEntry<K, V> {
        check(!hasMinSize())
        return childrenRange(if (first) 0 else children.lastIndex)
            .mergeWithSiblingBeforeRemoveIfNecessary()
            .let { if (first) it.removeFirstEntry() else it.removeLastEntry() }
            .splitIfNecessary()
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return config.nodeDeserializer
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        // return children
        TODO()
    }

    override fun serialize(): String {
        return separatorKeys.joinToString(SerializationSeparators.LEVEL2) {
            config.keySerializer(it)
        } + SerializationSeparators.LEVEL1 + children.joinToString(SerializationSeparators.LEVEL2) {
            // it.getHashString()
            TODO()
        }
    }
}
