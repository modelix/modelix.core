package org.modelix.datastructures.hamt

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference

sealed class MapChangeEvent<V : IObjectData>
data class EntryAddedEvent<V : IObjectData>(val key: Long, val value: ObjectReference<V>) : MapChangeEvent<V>()
data class EntryRemovedEvent<V : IObjectData>(val key: Long, val value: ObjectReference<V>) : MapChangeEvent<V>()
data class EntryChangedEvent<V : IObjectData>(val key: Long, val oldValue: ObjectReference<V>, val newValue: ObjectReference<V>) : MapChangeEvent<V>()
