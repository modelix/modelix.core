package org.modelix.model.async

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.completable.completableOfEmpty
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.toMaybeNotNull
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.toSingle
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.getSynchronous

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

    override fun <T : Any> get(key: ObjectHash<T>): Maybe<T> {
        return store.get(key.hash, key.deserializer).toMaybeNotNull()
    }

    override fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>> {
        val entries = store.getAll(keys.map { it.toKVEntryReference<IKVValue>() }.toList().getSynchronous(), emptyList())
        return keys.map { it to entries[it.hash] }
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>> {
        val entries = store.getAll(keys.map { it.toKVEntryReference() }, emptyList())
        return keys.associate { it to entries[it.hash] }.toSingle()
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable {
        entries.forEach { store.put(it.key.hash, it.value, it.value.serialize()) }
        return completableOfEmpty()
    }
}
