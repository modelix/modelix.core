package org.modelix.datastructures.hamt

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.asObject
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.ifEmpty

data class HamtTree<K, V : Any>(val root: Object<HamtNode<K, V>>) : IPersistentMap<K, V> {
    constructor(rootData: HamtNode<K, V>) : this(rootData.asObject(rootData.config.graph))

    val config: HamtNode.Config<K, V> get() = root.data.config
    val graph: IObjectGraph get() = config.graph

    private fun <T> query(provider: () -> IStream.One<T>): T = graph.getStreamExecutor().query(provider)

    private fun newTree(newRoot: () -> IStream.ZeroOrOne<HamtNode<K, V>>): IStream.One<HamtTree<K, V>> {
        return newRoot().ifEmpty { HamtInternalNode.createEmpty(config) }.map {
            copy(root = it.asObject(graph))
        }
    }

    override fun put(key: K, value: V) = newTree {
        root.data.put(key, value, graph)
    }
    override fun get(key: K): IStream.ZeroOrOne<V> = root.data.get(key)
    override fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> {
        return root.data.getAll(keys, 0).mapNotNull { it.first to (it.second ?: return@mapNotNull null) }
    }
    override fun remove(key: K) = newTree { root.data.remove(key, graph) }
    fun getEntries(): IStream.Many<Pair<K, V>> = root.data.getEntries()

    override fun getAll(): IStream.Many<Pair<K, V>> {
        return root.data.getEntries()
    }

    override fun getKeyTypeConfig(): IDataTypeConfiguration<K> {
        return config.keyConfig
    }

    override fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        return newTree { root.data.putAll(entries.toList(), 0, graph) }
    }

    override fun removeAll(keys: Iterable<K>): IStream.One<IPersistentMap<K, V>> {
        return newTree {
            keys.fold<K, IStream.ZeroOrOne<HamtNode<K, V>>>(IStream.of(root.data)) { acc, key ->
                acc.flatMapZeroOrOne { it.remove(key, 0, graph) }
            }
        }
    }

    override fun removeAllEntries(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        return removeAll(entries.map { it.first })
    }

    override fun getAllValues(keys: Iterable<K>): IStream.Many<V> {
        return root.data.getAll(keys, 0).map { checkNotNull(it.second) { "Not found: ${it.first}" } }
    }

    override fun getAllValues(): IStream.Many<V> {
        return root.data.getEntries().map { it.second }
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return graph.getStreamExecutor()
    }

    override fun asObject(): Object<*> {
        return root
    }

    override fun getChanges(
        oldMap: IPersistentMap<K, V>,
        changesOnly: Boolean,
    ): IStream.Many<MapChangeEvent<K, V>> {
        oldMap as HamtTree<K, V>
        return root.data.getChanges(oldMap.root.data, 0, changesOnly)
    }
}
