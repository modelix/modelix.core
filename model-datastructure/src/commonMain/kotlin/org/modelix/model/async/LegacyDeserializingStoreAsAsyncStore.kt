package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

class LegacyDeserializingStoreAsAsyncStore(val store: IDeserializingKeyValueStore) :
    IAsyncObjectStore, IStreamExecutorProvider by store {

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.keyValueStore
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return store
    }

    override fun clearCache() {}

    override fun <T : Any> getIfCached(key: ObjectRequest<T>): T? {
        return store.getIfCached(key.hash, key.deserializer, false)
    }

    override fun <T : Any> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        return IStream.ofNotNull(store.get(key.hash, key.deserializer))
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, Any?>> {
        return keys.toList().flatMap { keysAsList ->
            val entries = store.getAll(keysAsList as List<ObjectRequest<IObjectData>>)
            keys.map { it to entries[it.hash] }
        }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, Any?>> {
        val entries = store.getAll(keys as List<ObjectRequest<IObjectData>>)
        return IStream.of(keys.associate { it to entries[it.hash] })
    }

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero {
        entries.forEach { store.put(it.key.hash, it.value, it.value.serialize()) }
        return IStream.zero()
    }
}
