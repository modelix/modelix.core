package org.modelix.model.persistent

import org.modelix.model.lazy.KVEntryReference

sealed class MapChangeEvent
data class EntryAddedEvent(val key: Long, val value: KVEntryReference<CPNode>) : MapChangeEvent()
data class EntryRemovedEvent(val key: Long, val value: KVEntryReference<CPNode>) : MapChangeEvent()
data class EntryChangedEvent(val key: Long, val oldValue: KVEntryReference<CPNode>, val newValue: KVEntryReference<CPNode>) : MapChangeEvent()
