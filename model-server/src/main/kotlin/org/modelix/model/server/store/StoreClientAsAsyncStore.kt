package org.modelix.model.server.store

import org.modelix.model.IKeyValueStore
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
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

    override fun <T : Any> getIfCached(key: ObjectRequest<T>): T? {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        return store.getIfCached(key.hash)?.let { key.deserializer(it) }
    }

    override fun <T : Any> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        val value = store.get(key.hash) ?: return IStream.empty()
        return IStream.of(key.deserializer(value))
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, Any?>> {
        return keys.toList().flatMap { keysList ->
            val keysMap = keysList.associateBy { it.hash }

            @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
            val serializedValues = store.getAll(keysMap.keys)
            IStream.many(
                serializedValues.map {
                    val ref = keysMap[it.key]!!
                    ref to it.value?.let { ref.deserializer(it) }
                },
            )
        }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, Any?>> {
        val keysMap = keys.associateBy { it.hash }

        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        val serializedValues = store.getAll(keysMap.keys)
        return IStream.of(
            serializedValues.map {
                val ref = keysMap[it.key]!!
                ref to it.value?.let { ref.deserializer(it) }
            }.toMap(),
        )
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        @OptIn(RequiresTransaction::class) // store is immutable and doesn't require transactions
        store.putAll(entries.entries.associate { it.key.hash to it.value.serialize() })
        return IStream.zero()
    }
}
