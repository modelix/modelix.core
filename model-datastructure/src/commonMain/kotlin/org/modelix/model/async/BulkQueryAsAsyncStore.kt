package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

@Deprecated("Use BulkAsyncStore")
class BulkQueryAsAsyncStore(val store: IDeserializingKeyValueStore, val bulkQuery: IBulkQuery) : IAsyncObjectStore {
    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.keyValueStore
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return store
    }

    override fun <T : Any> get(key: ObjectHash<T>): IStream.ZeroOrOne<T> {
        return bulkQuery.query(key.toKVEntryReference())
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): IStream.One<Map<ObjectHash<*>, Any?>> {
        throw UnsupportedOperationException()
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectHash<*>>): IStream.Many<Pair<ObjectHash<*>, Any?>> {
        throw UnsupportedOperationException()
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): IStream.Zero {
        throw UnsupportedOperationException()
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        throw UnsupportedOperationException()
    }
}
