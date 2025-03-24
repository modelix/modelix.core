package org.modelix.datastructures.hamt

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.streams.IStream

data class HamtTree<K, V : Any>(val root: HamtNode<K, V>) {
    val config: HamtNode.Config<K, V> get() = root.config
    val graph: IObjectGraph get() = config.graph

    private fun <T> query(provider: () -> IStream.One<T>): T = graph.getStreamExecutor().query(provider)

    private fun newTree(newRoot: () -> IStream.ZeroOrOne<HamtNode<K, V>>) = copy(
        root = graph.getStreamExecutor().query { newRoot().orNull() }
            ?: HamtInternalNode.createEmpty(root.config),
    )

    fun put(key: K, value: V) = newTree { root.put(key, value, root.config.graph) }
    fun get(key: K): V? = query { root.get(key).orNull() }
    fun getAll(keys: Iterable<K>): IStream.One<List<V?>> = root.getAll(keys)
    fun remove(key: K): HamtTree<K, V> = newTree { root.remove(key, graph) }
    fun getEntries(): IStream.Many<Pair<K, V>> = root.getEntries()
}
