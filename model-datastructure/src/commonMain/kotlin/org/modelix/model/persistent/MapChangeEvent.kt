package org.modelix.model.persistent

import org.modelix.datastructures.objects.ObjectReference

sealed class MapChangeEvent
data class EntryAddedEvent(val key: Long, val value: ObjectReference<CPNode>) : MapChangeEvent()
data class EntryRemovedEvent(val key: Long, val value: ObjectReference<CPNode>) : MapChangeEvent()
data class EntryChangedEvent(val key: Long, val oldValue: ObjectReference<CPNode>, val newValue: ObjectReference<CPNode>) : MapChangeEvent()
