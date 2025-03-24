package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream

data class BTree<K, V>(val root: BTreeNode<K, V>) {
    constructor(config: BTreeConfig<K, V>) : this(BTreeNodeLeaf(config, emptyList()))

    val graph: IObjectGraph get() = root.config.graph

    fun validate() {
        graph.getStreamExecutor().query {
            root.validate(true)
            @OptIn(DelicateModelixApi::class)
            check(root.getEntries().toList().getSynchronous().map { it.key }.toSet().size == root.getEntries().map { it.key }.count().getSynchronous()) {
                "duplicate entries: $root"
            }
            check(root.getEntries().map { it.key }.toList().getSynchronous().sortedWith(root.config.keyConfiguration) == root.getEntries().map { it.key }.toList().getSynchronous()) {
                "not sorted: $this"
            }
            IStream.of(Unit)
        }
    }
    fun put(key: K, value: V): BTree<K, V> = copy(root = root.put(key, value).createRoot())
    fun get(key: K): V? = root.get(key)
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> = root.getAll(keys)
    fun remove(key: K): BTree<K, V> = copy(root = root.remove(key).createRoot())
    fun getEntries(): IStream.Many<BTreeEntry<K, V>> = root.getEntries()
}
