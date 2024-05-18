/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.LRUCache
import org.modelix.model.persistent.IKVValue
import kotlin.jvm.JvmOverloads
import kotlin.jvm.Synchronized

class ObjectStoreCache @JvmOverloads constructor(
    override val keyValueStore: IKeyValueStore,
    cacheSize: Int = 100_000,
    private val defaultBatchSize: Int? = null,
    private val defaultPrefetchSize: Int? = null,
) : IDeserializingKeyValueStore {
    private val regularCache = LRUCache<String, Any>(cacheSize)
    private val prefetchCache = LRUCache<String, Any>(cacheSize)
    private var bulkQuery: Pair<IBulkQuery, IDeserializingKeyValueStore>? = null

    @Synchronized
    override fun newBulkQuery(wrapper: IDeserializingKeyValueStore, batchSize: Int?, prefetchSize: Int?): IBulkQuery {
        if (bulkQuery?.takeIf { it.second == wrapper } == null) {
            bulkQuery = keyValueStore.newBulkQuery(wrapper, batchSize ?: defaultBatchSize, prefetchSize ?: defaultPrefetchSize) to wrapper
        }
        return bulkQuery!!.first
    }

    @Synchronized
    override fun <T> getAll(hashes_: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        val hashes = hashes_.toList()
        val result: MutableMap<String?, T?> = HashMap()
        val nonCachedHashes: MutableList<String> = ArrayList(hashes.size)
        for (hash in hashes) {
            val deserialized = (regularCache[hash] ?: prefetchCache[hash]) as T?
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
                    regularCache[hash] = deserialized ?: NULL
                    result[hash] = deserialized
                }
            }
        }
        return hashes.map { key: String? -> result[key] as T }.asIterable()
    }

    @Synchronized
    override fun <T : IKVValue> getAll(
        regular: List<IKVEntryReference<T>>,
        prefetch: List<IKVEntryReference<T>>,
    ): Map<String, T?> {
        val regularHashes = regular.asSequence().map { it.getHash() }.toSet()
        val allRequests = regular.asSequence().plus(prefetch.asSequence())
        val deserializers = allRequests.associate { it.getHash() to it.getDeserializer() }
        val hashes = allRequests.map { it.getHash() }.toList()
        val result: MutableMap<String, T?> = HashMap()
        val nonCachedHashes: MutableList<String> = ArrayList(hashes.size)
        for (hash in hashes) {
            val deserialized = regularCache.get(hash, updatePosition = regularHashes.contains(hash)) ?: prefetchCache.get(hash)
            if (deserialized == null) {
                nonCachedHashes.add(hash)
            } else {
                result[hash] = if (deserialized === NULL) null else deserialized as T?
            }
        }
        if (nonCachedHashes.isNotEmpty()) {
            for ((hash, serialized) in keyValueStore.getAll(nonCachedHashes)) {
                if (serialized == null) {
                    result[hash] = null
                } else {
                    val deserialized = deserializers[hash]!!(serialized)
                    (if (regularHashes.contains(hash)) regularCache else prefetchCache)[hash] = deserialized ?: NULL
                    result[hash] = deserialized
                }
            }
        }
        return result
    }

    @Synchronized
    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return get(hash, deserializer, false, false)
    }

    private fun <T> get(hash: String, deserializer: (String) -> T, ifCached: Boolean, isPrefetch: Boolean): T? {
        var deserialized = (regularCache.get(hash, updatePosition = !isPrefetch) ?: prefetchCache.get(hash)) as T?
        if (deserialized == null) {
            val serialized = (if (ifCached) keyValueStore.getIfCached(hash) else keyValueStore[hash] ) ?: return null
            deserialized = deserializer(serialized)
            (if (isPrefetch) prefetchCache else regularCache)[hash] = deserialized ?: NULL
        }
        return if (deserialized === NULL) null else deserialized
    }

    @Synchronized
    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return get(hash, deserializer, true, isPrefetch)
    }

    @Synchronized
    override fun put(hash: String, deserialized: Any, serialized: String) {
        keyValueStore.put(hash, serialized)
        regularCache[hash] = deserialized ?: NULL
        prefetchCache.remove(hash)
    }

    @Synchronized
    fun clearCache() {
        regularCache.clear()
        prefetchCache.clear()
    }

    override fun prefetch(hash: String) {
        keyValueStore.prefetch(hash)
    }

    companion object {
        private val NULL = Any()
    }
}
