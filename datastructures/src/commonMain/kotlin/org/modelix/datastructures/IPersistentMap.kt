package org.modelix.datastructures

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

/**
 * Also works as a multimap. A multimap can store multiple entries with the same key. It depends on the configuration of
 * the implementation whether it behaves as a multimap or not.
 */
interface IPersistentMap<K, V> : IStreamExecutorProvider {
    fun asObject(): Object<*>
    fun getKeyTypeConfig(): IDataTypeConfiguration<K>

    fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>>
    fun removeAll(keys: Iterable<K>): IStream.One<IPersistentMap<K, V>>
    fun removeAllEntries(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>>

    fun getAllValues(keys: Iterable<K>): IStream.Many<V>
    fun getAllValues(): IStream.Many<V>
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>>
    fun getAll(): IStream.Many<Pair<K, V>>

    fun get(key: K): IStream.ZeroOrOne<V> = getAll(listOf(key)).filter { it.first == key }.map { it.second }.firstOrEmpty()
    fun put(key: K, value: V): IStream.One<IPersistentMap<K, V>> = putAll(listOf(key to value))
    fun remove(key: K): IStream.One<IPersistentMap<K, V>> = removeAll(listOf(key))

    fun getChanges(oldMap: IPersistentMap<K, V>, changesOnly: Boolean): IStream.Many<MapChangeEvent<K, V>>
}

interface IPersistentMapRootData<K, V> : IObjectData {
    fun createMapInstance(self: Object<IPersistentMapRootData<K, V>>): IPersistentMap<K, V>
}

fun <K, V> Object<IPersistentMapRootData<K, V>>.createMapInstance(): IPersistentMap<K, V> {
    return data.createMapInstance(this)
}

fun <K, V> ObjectReference<IPersistentMapRootData<K, V>>.createMapInstance(): IStream.One<IPersistentMap<K, V>> {
    return resolve().map { it.createMapInstance() }
}
