package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.LegacyDeserializingStoreAsAsyncStore
import org.modelix.model.persistent.IKVValue

interface IDeserializingKeyValueStore {
    fun newBulkQuery(): IBulkQuery = newBulkQuery(this)
    fun newBulkQuery(wrapper: IDeserializingKeyValueStore = this, config: BulkQueryConfiguration? = null): IBulkQuery = keyValueStore.newBulkQuery(wrapper, config ?: BulkQueryConfiguration())
    val keyValueStore: IKeyValueStore
    operator fun <T> get(hash: String, deserializer: (String) -> T): T?
    fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T?
    fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T>
    fun <T : IKVValue> getAll(regular: List<IKVEntryReference<T>>, prefetch: List<IKVEntryReference<T>>): Map<String, T?> = throw UnsupportedOperationException()
    fun put(hash: String, deserialized: Any, serialized: String)

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetch(hash: String)

    fun getAsyncStore(): IAsyncObjectStore = LegacyDeserializingStoreAsAsyncStore(this) // LegacyDeserializingStoreAsAsyncStore(this) // BulkAsyncStore(CachingAsyncStore(LegacyKeyValueStoreAsAsyncStore(keyValueStore)))
}
