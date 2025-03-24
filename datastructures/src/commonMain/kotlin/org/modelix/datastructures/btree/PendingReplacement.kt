package org.modelix.datastructures.btree

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
