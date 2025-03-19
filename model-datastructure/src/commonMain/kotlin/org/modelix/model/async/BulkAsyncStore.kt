package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.streams.BulkRequestStreamExecutor
import org.modelix.streams.IBulkExecutor
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.filterNotNull

class BulkAsyncStore(
    val store: IAsyncObjectStore,
    batchSize: Int = 5000,
) : IAsyncObjectStore {

    private val bulkExecutor = BulkRequestStreamExecutor<ObjectRequest<*>, IObjectData?>(
        object : IBulkExecutor<ObjectRequest<*>, IObjectData?> {
            override fun execute(keys: List<ObjectRequest<*>>): Map<ObjectRequest<*>, IObjectData?> {
                return store.getStreamExecutor().query { store.getAllAsMap(keys) }
            }

            override suspend fun executeSuspending(keys: List<ObjectRequest<*>>): Map<ObjectRequest<*>, IObjectData?> {
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

    override fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        return getIfCached(key)?.let { IStream.of(it) }
            ?: bulkExecutor.enqueue(key).filterNotNull().upcast<T>()
    }

    override fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T? {
        return store.getIfCached(key)
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> {
        return keys.flatMap { key -> get(key).orNull().map { key to it } }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>> {
        return getAllAsStream(IStream.many(keys)).toMap({ it.first }, { it.second })
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        return store.putAll(entries)
    }
}

fun <T : IObjectData> IStream.ZeroOrOne<IObjectData>.upcast(): IStream.ZeroOrOne<T> = this as IStream.ZeroOrOne<T>
