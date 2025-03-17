package org.modelix.model.lazy

import org.modelix.kotlin.utils.runSynchronized
import org.modelix.model.IKeyValueStore
import org.modelix.model.async.CachingAsyncStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStreamExecutorProvider
import kotlin.jvm.JvmOverloads
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
    return CachingAsyncStore(NonCachingObjectStore(keyValueStore).getAsyncStore(), cacheSize)
}

@Deprecated("Use NonCachingObjectStore in combination with CachingAsyncStore")
class ObjectStoreCache
@JvmOverloads
@Deprecated("Use createObjectStoreCache", ReplaceWith("createObjectStoreCache(keyValueStore, cacheSize)"))
constructor(
    override val keyValueStore: IKeyValueStore,
    val cacheSize: Int,
) : IDeserializingKeyValueStore, IStreamExecutorProvider by keyValueStore {

    @Deprecated("Use createObjectStoreCache", ReplaceWith("createObjectStoreCache(keyValueStore)"))
    constructor(keyValueStore: IKeyValueStore) : this(keyValueStore, 100_000)

    private val cache = LRUCache<String, Any>(cacheSize)

    override fun getAsyncStore(): IAsyncObjectStore {
        return CachingAsyncStore(NonCachingObjectStore(keyValueStore).getAsyncStore(), cacheSize = cacheSize)
    }

    override fun <T> getAll(hashes_: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        val hashes = hashes_.toList()
        val result: MutableMap<String?, T?> = LinkedHashMap()
        val nonCachedHashes: MutableList<String> = ArrayList(hashes.size)
        for (hash in hashes) {
            val deserialized = (cache[hash]) as T?
            if (deserialized == null) {
                nonCachedHashes.add(hash)
            } else {
                result[hash] = if (deserialized === NULL) null else deserialized
            }
        }
        if (nonCachedHashes.isNotEmpty()) {
            for ((hash, serialized) in keyValueStore.getAll(nonCachedHashes)) {
                if (serialized == null) {
                    result[hash] = null
                } else {
                    val deserialized: T? = deserializer(hash, serialized)
                    cache[hash] = deserialized ?: NULL
                    result[hash] = deserialized
                }
            }
        }
        return hashes.map { key: String? -> result[key] as T }.asIterable()
    }

    override fun <T : IKVValue> getAll(
        regular: List<IKVEntryReference<T>>,
    ): Map<String, T?> {
        val regularHashes = regular.asSequence().map { it.getHash() }.toSet()
        val allRequests = regular.asSequence()
        val deserializers = allRequests.associate { it.getHash() to it.getDeserializer() }
        val hashes = allRequests.map { it.getHash() }.toList()
        val result: MutableMap<String, T?> = LinkedHashMap()
        val nonCachedHashes: MutableList<String> = ArrayList(hashes.size)
        runSynchronized(this) {
            for (hash in hashes) {
                val deserialized = cache.get(hash, updatePosition = regularHashes.contains(hash))
                if (deserialized == null) {
                    nonCachedHashes.add(hash)
                } else {
                    result[hash] = if (deserialized === NULL) null else deserialized as T?
                }
            }
        }
        if (nonCachedHashes.isNotEmpty()) {
            val requestResult = keyValueStore.getAll(nonCachedHashes) // call to keyValueStore without lock to avoid deadlocks
            runSynchronized(this) {
                for ((hash, serialized) in requestResult) {
                    if (serialized == null) {
                        result[hash] = null
                    } else {
                        val deserialized = deserializers[hash]!!(serialized)
                        cache[hash] = deserialized ?: NULL
                        result[hash] = deserialized
                    }
                }
            }
        }
        return result
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return get(hash, deserializer, false, false)
    }

    private fun <T> get(hash: String, deserializer: (String) -> T, ifCached: Boolean, isPrefetch: Boolean): T? {
        var deserialized = runSynchronized(this) {
            (cache.get(hash, updatePosition = !isPrefetch)) as T?
        }
        if (deserialized == null) {
            val serialized = (if (ifCached) keyValueStore.getIfCached(hash) else keyValueStore[hash]) ?: return null
            deserialized = deserializer(serialized)
            runSynchronized(this) {
                cache[hash] = deserialized ?: NULL
            }
        }
        return if (deserialized === NULL) null else deserialized
    }

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return get(hash, deserializer, true, isPrefetch)
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        keyValueStore.put(hash, serialized)
        runSynchronized(this) {
            cache[hash] = deserialized ?: NULL
        }
    }

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    companion object {
        private val NULL = Any()
    }
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
