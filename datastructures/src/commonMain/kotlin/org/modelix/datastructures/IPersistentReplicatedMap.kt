package org.modelix.datastructures

import org.modelix.streams.IStream

interface IPersistentReplicatedMap<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V): IPersistentReplicatedMap<K, V>
    fun remove(key: K): IPersistentReplicatedMap<K, V>

    fun putAll(entries: IStream.Many<Pair<K, V>>): IStream.One<IPersistentReplicatedMap<K, V>>
    fun removeAll(entries: IStream.Many<K>): IStream.One<IPersistentReplicatedMap<K, V>>
    fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>>
    fun getAll(): IStream.Many<Pair<K, V>>
}
