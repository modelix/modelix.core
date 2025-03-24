package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.ObjectReference

data class ChildrenRange<K, V>(val parent: BTreeNodeInternal<K, V>, val range: IntRange) {
    constructor(parent: BTreeNodeInternal<K, V>, index: Int) : this(parent, index..index)

    val config: BTreeConfig<K, V> get() = parent.config

    private fun BTreeNode<K, V>.toRef() = config.graph.fromCreated(this)
    private fun ObjectReference<BTreeNode<K, V>>.deref() = resolveNow().data

    fun replaceWith(newChild: BTreeNode<K, V>): ChildrenRange<K, V> {
        return copy(
            parent = parent.copy(
                separatorKeys = if (range.size() > 1) {
                    parent.separatorKeys.take(range.first) + parent.separatorKeys.drop(range.last)
                } else {
                    parent.separatorKeys
                },
                children = parent.children.take(range.first) + newChild.toRef() + parent.children.drop(range.last + 1),
            ),
        )
    }

    fun replaceWith(leftChild: BTreeNode<K, V>, separatorKey: K, rightChild: BTreeNode<K, V>): Replacement<K, V> {
        return parent.copy(
            separatorKeys = parent.separatorKeys.take(range.first) + separatorKey + parent.separatorKeys.drop(range.last),
            children = parent.children.take(range.first) + leftChild.toRef() + rightChild.toRef() + parent.children.drop(range.last + 1),
        ).splitIfNecessary()
    }

    fun merge(): PendingReplacement<K, V> {
        return when (range.size()) {
            1 -> PendingReplacement(this, Replacement.Single(firstInRange().deref()))
            2 -> PendingReplacement(this, Replacement.Single(firstInRange().deref().mergeWithSibling(firstSeparator(), lastInRange().deref())))
            else -> error("range spans across more than two children")
        }
    }

    fun mergeWithSiblingBeforeRemoveIfNecessary(): PendingReplacement<K, V> {
        check(range.size() == 1)
        return if (firstInRange().deref().hasMinSize()) {
            extendToSmaller().merge()
        } else {
            PendingReplacement(this, Replacement.Single(firstInRange().deref()))
        }
    }

    fun firstInRange() = parent.children[range.first]
    fun lastInRange() = parent.children[range.last]
    fun firstSeparator() = parent.separatorKeys[range.first]
    fun size() = range.size()

    fun extendToSmaller(): ChildrenRange<K, V> {
        return if (range.first <= 0) {
            extendRight()
        } else if (range.last >= parent.children.lastIndex) {
            extendLeft()
        } else if (parent.children[range.first - 1].deref().size() < parent.children[range.last + 1].deref().size()) {
            extendLeft()
        } else {
            extendRight()
        }
    }
    fun extendLeft() = ChildrenRange(parent, (range.first - 1)..range.last)
    fun extendRight() = ChildrenRange(parent, range.first..(range.last + 1))
}

private fun IntRange.size() = (last - first + 1)
