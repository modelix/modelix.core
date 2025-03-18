package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.streams.BulkRequestStreamExecutor
import org.modelix.streams.IBulkExecutor
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

class BulkAsyncStore(
    val store: IAsyncObjectStore,
    batchSize: Int = 5000,
) : IAsyncObjectStore {

    private val bulkExecutor = BulkRequestStreamExecutor<ObjectRequest<*>, Any?>(
        object : IBulkExecutor<ObjectRequest<*>, Any?> {
            override fun execute(keys: List<ObjectRequest<*>>): Map<ObjectRequest<*>, Any?> {
                return store.getStreamExecutor().query { store.getAllAsMap(keys) }
            }

            override suspend fun executeSuspending(keys: List<ObjectRequest<*>>): Map<ObjectRequest<*>, Any?> {
                return store.getStreamExecutor().querySuspending { store.getAllAsMap(keys) }
            }
        },
        batchSize = batchSize,
    )

    override fun clearCache() {
        store.clearCache()
    }

    override fun getStreamExecutor(): IStreamExecutor = bulkExecutor

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.getLegacyKeyValueStore()
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun <T : Any> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        return getIfCached(key)?.let { IStream.of(it) } ?: bulkExecutor.enqueue(key)
    }

    override fun <T : Any> getIfCached(key: ObjectRequest<T>): T? {
        return store.getIfCached(key)
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, Any?>> {
        return keys.flatMap { key -> get(key).orNull().map { key to it } }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, Any?>> {
        return getAllAsStream(IStream.many(keys)).toMap({ it.first }, { it.second })
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        return store.putAll(entries)
    }
}
