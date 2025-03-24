package org.modelix.datastructures.btree

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
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot())
}
