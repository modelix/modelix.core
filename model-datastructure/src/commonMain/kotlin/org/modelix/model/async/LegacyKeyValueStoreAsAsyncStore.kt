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

    override fun <T : Any> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        val value = store.get(key.hash) ?: return IStream.empty()
        return IStream.of(key.deserializer(value))
    }

    override fun <T : Any> getIfCached(key: ObjectRequest<T>): T? {
        return null
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, Any?>> {
        return keys.toList().flatMap { keysList ->
            val keysMap = keysList.associateBy { it.hash }
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
        val serializedValues = store.getAll(keysMap.keys)
        return IStream.of(
            serializedValues.map {
                val ref = keysMap[it.key]!!
                ref to it.value?.let { ref.deserializer(it) }
            }.toMap(),
        )
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        store.putAll(entries.entries.associate { it.key.hash to it.value.serialize() })
        return IStream.zero()
    }
}
