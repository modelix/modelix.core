package org.modelix.datastructures.patricia

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import org.modelix.datastructures.objects.asObject
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

/**
 * Entries with the same prefix are put into the same subtree. The prefix has a variable length.
 */
class PatriciaTrie<K, V : Any>(
    val config: PatriciaTrieConfig<K, V>,
    val root: Object<PatriciaNode<V>>,
) : IPersistentMap<K, V>, IStreamExecutorProvider by root.graph {
    constructor(config: PatriciaTrieConfig<K, V>) : this(config, PatriciaNode(config).asObject(config.graph))
    constructor(root: Object<PatriciaNode<V>>) : this(root.data.config as PatriciaTrieConfig<K, V>, root)

    override fun asObject(): Object<*> {
        return root
    }

    override fun getKeyTypeConfig(): IDataTypeConfiguration<K> {
        return config.keyConfig
    }

    private fun keyAsString(key: K): String {
        return config.keyConfig.serialize(key)
    }

    private fun withNewRoot(newRoot: PatriciaNode<V>?): PatriciaTrie<K, V> {
        return PatriciaTrie(config, (newRoot ?: PatriciaNode(config)).asObject(config.graph))
    }

    override fun get(key: K): IStream.ZeroOrOne<V> {
        return root.data.get(keyAsString(key))
    }

    override fun put(key: K, value: V): IStream.One<PatriciaTrie<K, V>> {
        val keyString = keyAsString(key)
        return root.data.put(keyString, value).orNull().map { withNewRoot(it) }
    }

    fun slice(prefix: CharSequence): IStream.One<PatriciaTrie<K, V>> {
        return root.data.slice(prefix).orNull().map { withNewRoot(it) }
    }

    override fun remove(key: K): IStream.One<PatriciaTrie<K, V>> {
        return root.data.put(keyAsString(key), null).orNull().map { withNewRoot(it) }
    }

    override fun getAllValues(): IStream.Many<V> {
        return root.data.getEntries("").map { it.second }
    }

    override fun getAllValues(keys: Iterable<K>): IStream.Many<V> {
        TODO("Not yet implemented")
    }

    override fun getAll(): IStream.Many<Pair<K, V>> {
        return root.data.getEntries("").map { config.keyConfig.deserialize(it.first.toString()) to it.second }
    }

    override fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> {
        TODO("Not yet implemented")
    }

    override fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        // TODO performance
        return entries.fold(IStream.of(this)) { acc, entry -> acc.flatMapOne { it.put(entry.first, entry.second) } }
    }

    override fun removeAll(keys: Iterable<K>): IStream.One<IPersistentMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun removeAllEntries(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun getChanges(
        oldMap: IPersistentMap<K, V>,
        changesOnly: Boolean,
    ): IStream.Many<MapChangeEvent<K, V>> {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PatriciaTrie<*, *>

        if (config != other.config) return false
        if (root.getHash() != other.root.getHash()) return false

        return true
    }

    override fun toString(): String {
        return root.getHash().toString()
    }

    override fun hashCode(): Int {
        var result = config.hashCode()
        result = 31 * result + root.getHash().hashCode()
        return result
    }

    companion object {
        fun withStrings(graph: IObjectGraph) = PatriciaTrie(
            config = PatriciaTrieConfig(
                graph = graph,
                keyConfig = StringDataTypeConfiguration(),
                valueConfig = StringDataTypeConfiguration(),
            ),
        )
    }
}
