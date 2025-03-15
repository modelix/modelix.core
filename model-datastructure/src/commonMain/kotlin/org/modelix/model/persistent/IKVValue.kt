package org.modelix.model.persistent

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.streams.IStream
import org.modelix.streams.plus

/**
 * Serializable object that can be stored in a key value store
 */
interface IKVValue {
    var isWritten: Boolean
    fun serialize(): String
    val hash: String
    fun getDeserializer(): (String) -> IKVValue
    fun getReferencedEntries(): List<KVEntryReference<IKVValue>>
    fun objectDiff(oldObject: IKVValue?, store: IAsyncObjectStore): IStream.Many<IKVValue> {
        return if (oldObject?.hash == hash) IStream.empty() else getAllObjects(store)
    }
}

fun IKVValue.getAllObjects(store: IAsyncObjectStore): IStream.Many<IKVValue> {
    val descendants = IStream.many(getReferencedEntries())
        .flatMap {
            it.getValue(store).flatMap { it.getAllObjects(store) }
        }
    return IStream.of(this) + descendants
}
