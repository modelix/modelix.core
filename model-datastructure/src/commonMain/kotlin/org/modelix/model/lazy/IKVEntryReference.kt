package org.modelix.model.lazy

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

interface IKVEntryReference<out E : IKVValue> {
    fun getHash(): String
    fun getValue(store: IDeserializingKeyValueStore): E
    fun getValue(store: IAsyncObjectStore): IStream.One<E>
    fun isWritten(): Boolean
    fun getUnwrittenValue(): E
    fun getDeserializer(): (String) -> E
    fun write(store: IDeserializingKeyValueStore)
    fun write(store: IAsyncObjectStore) = write(store.getLegacyObjectStore())
}
