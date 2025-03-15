package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

class LegacyDeserializingStoreAsAsyncStore(val store: IDeserializingKeyValueStore) : IAsyncObjectStore {

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.keyValueStore
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return store
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return store.getIfCached(key.hash, key.deserializer, false)
    }

    override fun <T : Any> get(key: ObjectHash<T>): IStream.ZeroOrOne<T> {
        return IStream.ofNotNull(store.get(key.hash, key.deserializer))
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectHash<*>>): IStream.Many<Pair<ObjectHash<*>, Any?>> {
        val entries = store.getAll(keys.map { it.toKVEntryReference<IKVValue>() }.toList().getSynchronous(), emptyList())
        return keys.map { it to entries[it.hash] }
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): IStream.One<Map<ObjectHash<*>, Any?>> {
        val entries = store.getAll(keys.map { it.toKVEntryReference() }, emptyList())
        return IStream.of(keys.associate { it to entries[it.hash] })
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): IStream.Zero {
        entries.forEach { store.put(it.key.hash, it.value, it.value.serialize()) }
        return IStream.zero()
    }
}
