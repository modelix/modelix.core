package org.modelix.model.async

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.autoConnect
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.publish
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.map
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.LRUCache
import org.modelix.model.persistent.IKVValue

class CachingAsyncStore(val store: IAsyncObjectStore, cacheSize: Int = 100_000) : IAsyncObjectStore {
    private val cache = LRUCache<ObjectHash<*>, Any>(cacheSize)

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.getLegacyKeyValueStore()
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        // delegating to store.getLegacyObjectStore() would bypass caching
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun <T : Any> get(key: ObjectHash<T>): Maybe<T> {
        val cached = runSynchronized(cache) { cache.get(key) }
        if (cached != null) return maybeOf(cached as T)
        return store.get(key).map { value ->
            runSynchronized(cache) {
                cache.set(key, value)
            }
            value
        }
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return runSynchronized(cache) { cache.get(key) as T? } ?: store.getIfCached(key)
    }

    override fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>> {
        val fromCache = keys.map { key ->
            runSynchronized(cache) { key to cache.get(key) }
        }.publish().autoConnect(2)

        val cached = fromCache.filter { it.second != null }
        val nonCached = fromCache.filter { it.second == null }.map { it.first }
        val fromStore = store.getAllAsStream(nonCached).map { entry ->
            runSynchronized(cache) {
                entry.second?.let { value -> cache[entry.first] = value }
            }
            entry
        }
        return cached.concatWith(fromStore)
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>> {
        val fromCache = LinkedHashMap<ObjectHash<*>, Any?>()
        val missingKeys = ArrayList<ObjectHash<*>>()
        runSynchronized(cache) {
            for (key in keys) {
                val value = cache.get(key)
                if (value == null) {
                    missingKeys.add(key)
                } else {
                    fromCache[key] = value
                }
            }
        }
        return store.getAllAsMap(missingKeys).map { fromStore ->
            runSynchronized(cache) {
                for (entry in fromStore) {
                    cache[entry.key] = entry.value ?: continue
                }
            }
            fromCache + fromStore
        }
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable {
        runSynchronized(cache) {
            for (entry in entries) {
                cache.set(entry.key, entry.value)
            }
        }
        return store.putAll(entries)
    }
}
