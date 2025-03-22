package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.async.CachingAsyncStore
import org.modelix.model.async.LegacyKeyValueStoreAsAsyncStore
import kotlin.jvm.Synchronized

class CacheConfiguration : BulkQueryConfiguration() {
    /**
     * Size of the cache for regularly requested objects.
     */
    var cacheSize: Int = defaultCacheSize

    companion object {
        var defaultCacheSize: Int = 100_000
    }
}

fun createObjectStoreCache(keyValueStore: IKeyValueStore, cacheSize: Int = 100_000): CachingAsyncStore {
    return CachingAsyncStore(LegacyKeyValueStoreAsAsyncStore(keyValueStore), cacheSize)
}

@Deprecated("Use createObjectStoreCache(...)..getLegacyObjectStore()")
fun ObjectStoreCache(keyValueStore: IKeyValueStore, cacheSize: Int = 100_000): IDeserializingKeyValueStore {
    return createObjectStoreCache(keyValueStore, cacheSize).getLegacyObjectStore()
}

class LRUCache<K : Any, V>(val maxSize: Int) {
    private val map: MutableMap<K, V> = LinkedHashMap()

    @Synchronized
    operator fun set(key: K, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > maxSize) map.remove(map.iterator().next().key)
    }

    @Synchronized
    operator fun get(key: K, updatePosition: Boolean = true): V? {
        return map[key]?.also { value ->
            if (updatePosition) {
                map.remove(key)
                map[key] = value as V
            }
        }
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
