package org.modelix.datastructures.btree

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
            check(!newNode.isOverfilled())
            return if (toReplace.size() == 1 && toReplace.firstInRange() === newNode) {
                Single(toReplace.parent) // nothing changed
            } else {
                toReplace.replaceWith(newNode).parent.splitIfNecessary()
            }
        }

        override fun createRoot(): BTreeNode<K, V> {
            return if (newNode is BTreeNodeInternal<K, V> && newNode.size() == 1) {
                newNode.children.single().resolveNow().data
            } else {
                newNode
            }
        }

        override fun expectSingle(): BTreeNode<K, V> = newNode
        override fun removeEntry(key: K): Replacement<K, V> = newNode.remove(key)
        override fun removeFirstEntry(): RemovedEntry<K, V> = newNode.removeFirstEntry()
        override fun removeLastEntry(): RemovedEntry<K, V> = newNode.removeLastEntry()
        override fun splitIfNecessary(): Replacement<K, V> = newNode.splitIfNecessary()
    }
    class Splitted<K, V>(
        val left: BTreeNode<K, V>,
        val separatorKey: K,
        val right: BTreeNode<K, V>,
    ) : Replacement<K, V>() {
        override fun apply(toReplace: ChildrenRange<K, V>): Replacement<K, V> {
            return toReplace.replaceWith(left, separatorKey, right)
        }

        override fun createRoot(): BTreeNode<K, V> {
            return BTreeNodeInternal(
                left.config,
                listOf(separatorKey),
                listOf(left.config.graph.fromCreated(left), right.config.graph.fromCreated(right)),
            )
        }
        override fun expectSingle(): BTreeNode<K, V> = throw IllegalStateException("Single node expected: $this")
        override fun splitIfNecessary(): Replacement<K, V> = this
        override fun removeEntry(key: K): Replacement<K, V> = error("unexpected")
        override fun removeFirstEntry(): RemovedEntry<K, V> = error("unexpected")
        override fun removeLastEntry(): RemovedEntry<K, V> = error("unexpected")
    }
}
