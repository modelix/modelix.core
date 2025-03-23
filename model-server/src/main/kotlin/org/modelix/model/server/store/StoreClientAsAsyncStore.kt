package org.modelix.model.server.store

import org.modelix.datastructures.objects.IObjectData
import org.modelix.model.IKeyValueStore
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor
import org.modelix.streams.withSequences

class StoreClientAsAsyncStore(val store: IStoreClient) : IAsyncObjectStore {
    override fun getStreamExecutor(): IStreamExecutor = SimpleStreamExecutor().withSequences()

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return StoreClientAsKeyValueStore(store)
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun clearCache() {}

    override fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T? {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        return store.getIfCached(key.hash)?.let { key.deserializer.deserialize(it, key.referenceFactory) }
    }

    override fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        val value = store.get(key.hash) ?: return IStream.empty()
        return IStream.of(key.deserializer.deserialize(value, key.referenceFactory))
    }

    override fun getAllAsStream(requestsStream: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> {
        return requestsStream.toList().flatMap { requestsList ->
            val requestsMap = requestsList.associateBy { it.hash }

            @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
            val serializedValues = store.getAll(requestsMap.keys)
            IStream.many(
                serializedValues.map {
                    val request = requestsMap[it.key]!!
                    request to it.value?.let { request.deserializer.deserialize(it, request.referenceFactory) }
                },
            )
        }
    }

    override fun getAllAsMap(requestList: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>> {
        val requestsMap = requestList.associateBy { it.hash }

        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        val serializedValues = store.getAll(requestsMap.keys)
        return IStream.of(
            serializedValues.map {
                val request = requestsMap[it.key]!!
                request to it.value?.let { request.deserializer.deserialize(it, request.referenceFactory) }
            }.toMap(),
        )
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        store.putAll(entries.entries.associate { it.key.hash to it.value.serialize() })
        return IStream.zero()
    }
}
