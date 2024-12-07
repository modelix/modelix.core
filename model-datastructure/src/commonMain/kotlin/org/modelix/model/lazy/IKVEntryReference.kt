package org.modelix.model.lazy

import com.badoo.reaktive.single.Single
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.IKVValue

interface IKVEntryReference<out E : IKVValue> {
    fun getHash(): String
    fun getValue(store: IDeserializingKeyValueStore): E
    fun getValue(store: IAsyncObjectStore): Single<E>
    fun isWritten(): Boolean
    fun getUnwrittenValue(): E
    fun getDeserializer(): (String) -> E
    fun write(store: IDeserializingKeyValueStore)
    fun write(store: IAsyncObjectStore) = write(AsyncStoreAsLegacyDeserializingStore(store))
}
