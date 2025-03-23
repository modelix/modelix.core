package org.modelix.datastructures.hamt

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference

sealed class MapChangeEvent<K, V : IObjectData>
data class EntryAddedEvent<K, V : IObjectData>(val key: K, val value: ObjectReference<V>) : MapChangeEvent<K, V>()
data class EntryRemovedEvent<K, V : IObjectData>(val key: K, val value: ObjectReference<V>) : MapChangeEvent<K, V>()
data class EntryChangedEvent<K, V : IObjectData>(val key: K, val oldValue: ObjectReference<V>, val newValue: ObjectReference<V>) : MapChangeEvent<K, V>()
