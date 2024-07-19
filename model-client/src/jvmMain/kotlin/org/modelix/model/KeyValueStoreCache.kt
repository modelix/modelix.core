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

package org.modelix.model

import org.apache.commons.collections4.map.LRUMap
import org.modelix.model.persistent.HashUtil
import org.modelix.model.util.StreamUtils.toStream
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

class KeyValueStoreCache(private val store: IKeyValueStore) : IKeyValueStoreWrapper {
    private val cache = Collections.synchronizedMap(LRUMap<String, String?>(300000))
    private val pendingPrefetches: MutableSet<String> = HashSet()
    private val activeRequests: MutableList<GetRequest> = ArrayList()
    override fun prefetch(key: String) {
        val processedKeys: MutableSet<String?> = HashSet()
        processedKeys.add(key)
        var newKeys: MutableList<String> = mutableListOf(key)
        while (newKeys.isNotEmpty() && processedKeys.size + newKeys.size <= 100000) {
            synchronized(pendingPrefetches) { newKeys.removeAll(pendingPrefetches) }
            val currentKeys = newKeys
            newKeys = ArrayList()
            var loadedEntries: Map<String, String?>?
            synchronized(pendingPrefetches) { pendingPrefetches.addAll(currentKeys) }
            try {
                loadedEntries = getAll(currentKeys)
                for ((loadedKey, loadedValue) in loadedEntries) {
                    processedKeys.add(loadedKey)
                    for (childKey in HashUtil.extractSha256(loadedValue)) {
                        if (processedKeys.contains(childKey)) {
                            continue
                        }
                        newKeys.add(childKey)
                    }
                }
            } finally {
                synchronized(pendingPrefetches) { pendingPrefetches.removeAll(currentKeys) }
            }
        }
    }

    override fun getWrapped(): IKeyValueStore = store

    override fun getPendingSize(): Int = store.getPendingSize()

    override fun get(key: String): String? {
        return getAll(setOf(key))[key]
    }

    override fun getIfCached(key: String): String? {
        return cache[key] ?: store.getIfCached(key)
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val remainingKeys = toStream(keys).collect(Collectors.toList())
        val result: MutableMap<String, String?> = LinkedHashMap(16, 0.75.toFloat(), false)
        synchronized(cache) {
            val itr = remainingKeys.iterator()
            while (itr.hasNext()) {
                val key = itr.next()
                val value = cache[key]
                // always put even if null to have the same order in the linked hash map as in the input
                result[key] = value
                if (value != null) {
                    itr.remove()
                }
            }
        }
        if (!remainingKeys.isEmpty()) {
            val requiredRequest: MutableList<GetRequest> = ArrayList()
            var newRequest: GetRequest? = null
            synchronized(activeRequests) {
                for (r in activeRequests) {
                    if (remainingKeys.stream().anyMatch { o: String? -> r.keys.contains(o) }) {
                        LOG.debug {
                            val intersection = remainingKeys.intersect(r.keys)
                            "Reusing an active request: " + intersection.firstOrNull() + " (" + intersection.size + ")"
                        }
                        requiredRequest.add(r)
                        remainingKeys.removeAll(r.keys)
                    }
                }
                if (!remainingKeys.isEmpty()) {
                    newRequest = GetRequest(HashSet(remainingKeys))
                    requiredRequest.add(newRequest!!)
                    activeRequests.add(newRequest!!)
                }
            }
            if (newRequest != null) {
                try {
                    newRequest!!.execute()
                } finally {
                    synchronized(activeRequests) { activeRequests.remove(newRequest!!) }
                }
            }
            for (req in requiredRequest) {
                val reqResult = req.waitForResult()
                for ((key, value) in reqResult) {
                    if (result.containsKey(key)) {
                        result[key] = value
                    }
                }
            }
        }
        return result
    }

    override fun listen(key: String, listener: IKeyListener) {
        store.listen(key, listener)
    }

    override fun put(key: String, value: String?) {
        cache[key] = value
        store.put(key, value)
    }

    override fun putAll(entries: Map<String, String?>) {
        entries.forEach { (key: String, value: String?) -> cache[key] = value }
        store.putAll(entries)
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        store.removeListener(key, listener)
    }

    private inner class GetRequest(val keys: Set<String>) {
        private val future: CompletableFuture<Map<String, String?>> = CompletableFuture()
        fun execute() {
            try {
                val entriesFromStore = store.getAll(keys)
                for ((key, value) in entriesFromStore) {
                    cache[key] = value
                }
                future.complete(entriesFromStore)
            } catch (ex: Exception) {
                future.completeExceptionally(ex)
            }
        }

        fun waitForResult(): Map<String, String?> {
            return future.get()
        }
    }

    companion object {
        private val LOG = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    }
}
