package org.modelix.datastructures.btree

import org.modelix.streams.IStream

data class BTree<K, V>(val root: BTreeNode<K, V>) {
    constructor(config: BTreeConfig<K, V>) : this(BTreeNodeLeaf(config, emptyList()))

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
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> = root.getAll(keys)
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot())
    fun getEntries(): Sequence<BTreeEntry<K, V>> = root.getEntries()
}
