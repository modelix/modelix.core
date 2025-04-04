package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.streams.IStream
import org.modelix.streams.getBlocking

data class BTree<K, V>(val root: BTreeNode<K, V>) {
    constructor(config: BTreeConfig<K, V>) : this(BTreeNodeLeaf(config, emptyList()))

    val graph: IObjectGraph get() = root.config.graph

    fun validate() {
        graph.getStreamExecutor().query {
            root.validate(true)
            check(root.getEntries().toList().getBlocking(graph).map { it.key }.toSet().size == root.getEntries().map { it.key }.count().getBlocking(graph)) {
                "duplicate entries: $root"
            }
            check(root.getEntries().map { it.key }.toList().getBlocking(graph).sortedWith(root.config.keyConfiguration) == root.getEntries().map { it.key }.toList().getBlocking(graph)) {
                "not sorted: $this"
            }
            IStream.of(Unit)
        }
    }
    fun put(key: K, value: V): BTree<K, V> = copy(root = root.put(key, value).createRoot())
    fun get(key: K): V? = root.get(key)
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> = root.getAll(keys)
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot())
    fun remove(key: K, value: V): BTree<K, V> = copy(root = root.remove(key).createRoot())
    fun getEntries(): IStream.Many<BTreeEntry<K, V>> = root.getEntries()
}
