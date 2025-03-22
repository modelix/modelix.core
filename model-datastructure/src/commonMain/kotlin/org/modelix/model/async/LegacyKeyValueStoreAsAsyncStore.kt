package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

class LegacyKeyValueStoreAsAsyncStore(val store: IKeyValueStore) : IAsyncObjectStore, IStreamExecutorProvider by store {
    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun clearCache() {}

    override fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        val value = store.get(key.hash) ?: return IStream.empty()
        return IStream.of(key.deserializer.deserialize(value, key.referenceFactory))
    }

    override fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T? {
        return null
    }

    override fun getAllAsStream(requests: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> {
        return requests.toList().flatMap { requestsList ->
            val requestsMap = requestsList.associateBy { it.hash }
            val serializedValues = store.getAll(requestsMap.keys)
            IStream.many(
                serializedValues.map { (hashString, serializedValue) ->
                    val request = requestsMap[hashString]!!
                    request to serializedValue?.let { request.deserializer.deserialize(it, request.referenceFactory) }
                },
            )
        }
    }

    override fun getAllAsMap(requestsList: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>> {
        val requestsMap = requestsList.associateBy { it.hash }
        val serializedValues = store.getAll(requestsMap.keys)
        return IStream.of(
            serializedValues.map { (hashString, serializedValue) ->
                val request = requestsMap[hashString]!!
                request to serializedValue?.let { request.deserializer.deserialize(it, request.referenceFactory) }
            }.toMap(),
        )
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        store.putAll(entries.entries.associate { it.key.hash to it.value.serialize() })
        return IStream.zero()
    }
}
