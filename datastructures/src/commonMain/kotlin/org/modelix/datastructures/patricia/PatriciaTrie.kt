package org.modelix.datastructures.patricia

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import org.modelix.datastructures.objects.asObject
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

typealias PatriciaTrieRef<K, V> = ObjectReference<PatriciaNode<K, V>>

/**
 * Entries with the same prefix are put into the same subtree. The prefix has a variable length.
 */
class PatriciaTrie<K, V : Any>(
    val config: PatriciaTrieConfig<K, V>,
    val root: Object<PatriciaNode<K, V>>,
) : IPersistentMap<K, V>, IStreamExecutorProvider by root.graph {
    constructor(config: PatriciaTrieConfig<K, V>) : this(config, PatriciaNode(config).asObject(config.graph))
    constructor(root: Object<PatriciaNode<K, V>>) : this(root.data.config as PatriciaTrieConfig<K, V>, root)

    override fun asObject(): Object<*> {
        return root
    }

    override fun getKeyTypeConfig(): IDataTypeConfiguration<K> {
        return config.keyConfig
    }

    private fun keyAsString(key: K): String {
        return config.keyConfig.serialize(key)
    }

    private fun withNewRoot(newRoot: PatriciaNode<K, V>?): PatriciaTrie<K, V> {
        return PatriciaTrie(config, (newRoot ?: PatriciaNode(config)).asObject(config.graph))
    }

    private fun IStream.ZeroOrOne<PatriciaNode<K, V>>.withNewRoot() = orNull().map { withNewRoot(it) }

    override fun get(key: K): IStream.ZeroOrOne<V> {
        return root.data.get(keyAsString(key))
    }

    override fun put(key: K, value: V): IStream.One<PatriciaTrie<K, V>> {
        val keyString = keyAsString(key)
        return root.data.put(keyString, value).withNewRoot()
    }

    /**
     * Returns a copy of the tree that contains only those entries starting with the given prefix.
     */
    fun slice(prefix: CharSequence): IStream.One<PatriciaTrie<K, V>> {
        return root.data.slice(prefix).withNewRoot()
    }

    /**
     * Removes all entries starting with the given [prefix] and adds all entries from [newEntries] starting with the
     * same prefix.
     * This allows efficiently updating a whole subtree from an external source without having to compare the new and
     * existing data. It can just be reimported and then compared by using the builtin diff support of this
     * data structure.
     */
    fun replaceSlice(prefix: CharSequence, newEntries: PatriciaTrie<K, V>): IStream.One<PatriciaTrie<K, V>> {
        if (prefix.isEmpty()) return IStream.of(newEntries)
        return newEntries.root.data.getSubtree(prefix).orNull().flatMapOne { replacement ->
            root.data.replaceSubtree(prefix, replacement).withNewRoot()
        }
    }

    override fun remove(key: K): IStream.One<PatriciaTrie<K, V>> {
        return root.data.put(keyAsString(key), null).withNewRoot()
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
        // TODO performance
        return IStream.many(keys).flatMap { key -> get(key).map { key to it } }
    }

    override fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        // TODO performance
        return entries.fold(IStream.of(this)) { acc, entry -> acc.flatMapOne { it.put(entry.first, entry.second) } }
    }

    override fun removeAll(keys: Iterable<K>): IStream.One<IPersistentMap<K, V>> {
        // TODO performance
        return keys.fold(IStream.of(this)) { acc, key -> acc.flatMapOne { it.remove(key) } }
    }

    override fun removeAllEntries(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>> {
        TODO("Not yet implemented")
    }

    override fun getChanges(
        oldMap: IPersistentMap<K, V>,
        changesOnly: Boolean,
    ): IStream.Many<MapChangeEvent<K, V>> {
        oldMap as PatriciaTrie<K, V>
        return root.data.getChanges("", oldMap.root.data, changesOnly)
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
