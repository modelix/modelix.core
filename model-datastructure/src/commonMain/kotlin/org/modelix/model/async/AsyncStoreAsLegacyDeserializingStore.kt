package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

private val ILLEGAL_DESERIALIZER: (String) -> Any = { error("deserialization not expected") }

@Deprecated("use IAsyncStore")
class AsyncStoreAsLegacyDeserializingStore(val store: IAsyncObjectStore) : IDeserializingKeyValueStore, IStreamExecutorProvider by store {

    override fun getAsyncStore(): IAsyncObjectStore {
        return store
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        val ref = ObjectRequest(hash, deserializer as ((String) -> Any))
        return getStreamExecutor().query { store.get(ref).orNull() } as T?
    }

    override val keyValueStore: IKeyValueStore
        get() = store.getLegacyKeyValueStore()

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return store.getIfCached(ObjectRequest(hash, deserializer as ((String) -> Any))) as T?
    }

    override fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        return getStreamExecutor().query {
            store.getAllAsStream(IStream.many(hash).map { hash -> ObjectRequest(hash, { deserializer(hash, it) as Any }) })
                .map { it.second as T }.toList()
        }
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        getStreamExecutor().query {
            store.putAll(mapOf(ObjectRequest(hash, ILLEGAL_DESERIALIZER) to deserialized as IObjectData)).asOne()
        }
    }

    override fun <T : IObjectData> getAll(
        regular: List<ObjectRequest<T>>,
    ): Map<String, T?> {
        return getStreamExecutor().query { store.getAllAsMap(regular) }.entries.associate { it.key.hash to it.value as T? }
    }
}
