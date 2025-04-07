package org.modelix.model.async

import org.modelix.datastructures.objects.IObjectData
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.LRUCache
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.plus

class CachingAsyncStore(val store: IAsyncObjectStore, cacheSize: Int = 100_000) :
    IAsyncObjectStore, IStreamExecutorProvider by store {
    private val cache = LRUCache<ObjectRequest<*>, IObjectData>(cacheSize)

    override fun clearCache() {
        cache.clear()
    }

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.getLegacyKeyValueStore()
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        // delegating to store.getLegacyObjectStore() would bypass caching
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T> {
        val cached = runSynchronized(cache) { cache.get(key) }
        if (cached != null) return IStream.of(cached as T)
        return store.get(key).map { value ->
            runSynchronized(cache) {
                cache.set(key, value)
            }
            value
        }
    }

    override fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T? {
        return runSynchronized(cache) { cache.get(key) as T? } ?: store.getIfCached(key)
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> {
        val fromCache: IStream.Many<Pair<ObjectRequest<*>, IObjectData?>> = keys.map { key ->
            runSynchronized(cache) { key to cache.get(key) }
        }

        return fromCache.splitMerge({ it.second != null }) { cached, nonCached ->
            val fromStore = store.getAllAsStream(nonCached.map { it.first }).map { entry ->
                runSynchronized(cache) {
                    entry.second?.let { value -> cache[entry.first] = value }
                }
                entry
            }
            cached + fromStore
        }
    }

    override fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>> {
        val fromCache = LinkedHashMap<ObjectRequest<*>, IObjectData?>()
        val missingKeys = ArrayList<ObjectRequest<*>>()
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

    override fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Completable {
        runSynchronized(cache) {
            for (entry in entries) {
                cache.set(entry.key, entry.value)
            }
        }
        return store.putAll(entries)
    }
}
