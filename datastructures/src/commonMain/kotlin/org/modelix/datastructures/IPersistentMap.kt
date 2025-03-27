package org.modelix.datastructures

import org.modelix.datastructures.objects.ObjectHash
import org.modelix.streams.IStream

/**
 * Also works as a multimap. A multimap can store multiple entries with the same key. It depends on the configuration of
 * the implementation whether it behaves as a multimap or not.
 */
interface IPersistentMap<K, V> {
    fun getHash(): ObjectHash

    fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>>
    fun removeAll(keys: Iterable<K>): IStream.One<IPersistentMap<K, V>>
    fun removeAll(entries: Iterable<Pair<K, V>>): IStream.One<IPersistentMap<K, V>>

    fun getAllValues(keys: Iterable<K>): IStream.Many<V>
    fun getAllValues(): IStream.Many<V>
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>>
    fun getAll(): IStream.Many<Pair<K, V>>

    fun get(key: K): IStream.ZeroOrOne<V> = getAll(listOf(key)).filter { it.first == key }.map { it.second }.firstOrEmpty()
    fun put(key: K, value: V): IStream.One<IPersistentMap<K, V>> = putAll(listOf(key to value))
    fun remove(key: K): IStream.One<IPersistentMap<K, V>> = removeAll(listOf(key))
}
