package org.modelix.datastructures

sealed class MapChangeEvent<K, V>
data class EntryAddedEvent<K, V>(val key: K, val value: V) : MapChangeEvent<K, V>()
data class EntryRemovedEvent<K, V>(val key: K, val value: V) : MapChangeEvent<K, V>()
data class EntryChangedEvent<K, V>(val key: K, val oldValue: V, val newValue: V) : MapChangeEvent<K, V>()
