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

import org.modelix.model.persistent.IKVValue
import kotlin.jvm.Synchronized

private typealias Deserializer = (IKVValue?) -> Unit
private val LOG = mu.KotlinLogging.logger {  }

/**
 * Not thread safe
 */
class BulkQuery(private val store: IDeserializingKeyValueStore, batchSize: Int? = null, prefetchSize: Int? = null) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private val prefetchOfferings: MutableList<PrefetchOffering> = ArrayList()
    private var processing = false
    private var currentPrefetchLevel: Int = 0
    private val batchSize: Int = batchSize ?: 5_000
    private val prefetchSize: Int = prefetchSize ?: (this.batchSize / 2)
    private val prefetchQueueSizeLimit: Int = (this.prefetchSize * 10).coerceAtLeast(this.batchSize * 2)

    init {
        require(this.prefetchSize <= this.batchSize) { "prefetch size ${this.prefetchSize} is greater than the batch size ${this.batchSize}" }
    }

    protected fun executeBulkQuery(refs: Iterable<KVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        val refsMap = refs.associateBy { it.getHash() }
        val result = HashMap<String, IKVValue?>()
        result += refs.filter { !it.isWritten() }.map { it.getHash() to it.getValue(store) }
        val keysToQuery = refs.filter { it.isWritten() }.map { it.getHash() }
        val queriedValues = store.getAll(keysToQuery) { key, serialized -> refsMap[key]!!.getDeserializer()(serialized) }
        result += keysToQuery.zip(queriedValues)
        return result
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IBulkQuery.Value<T?> {
        val cachedValue = store.getIfCached(hash.getHash(), hash.getDeserializer())
        if (cachedValue != null) {
            return constant(cachedValue)
        }

        if (queue.size >= batchSize && !processing) executeQuery()

        val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
        if (existingQueueElement != null) {
            existingQueueElement.prefetchLevel = minOf(existingQueueElement.prefetchLevel, currentPrefetchLevel)
            return existingQueueElement.value
        }

        val result = Value<T?>()
        queue.put(hash.getHash(), QueueElement<T>(hash, result, currentPrefetchLevel))
        return result
    }

    override fun offerPrefetch(body: () -> Unit) {
        prefetchOfferings.add(PrefetchOffering(currentPrefetchLevel + 1, body))
    }

    override fun <T> constant(value: T): IBulkQuery.Value<T> {
        return Value(value)
    }

    private fun runPrefetch(body: () -> Unit) = runPrefetch(currentPrefetchLevel + 1, body)

    private fun runPrefetch(newLevel: Int, body: () -> Unit) {
        val oldLevel = currentPrefetchLevel
        try {
            currentPrefetchLevel = newLevel
            body()
        } finally {
            currentPrefetchLevel = oldLevel
        }
    }

    override fun executeQuery() {
        if (processing) {
            throw RuntimeException("Already processing")
        }
        processing = true
        try {
            while (queue.isNotEmpty()) {
                while (prefetchOfferings.isNotEmpty()) {
                    val currentOfferings = prefetchOfferings.toList()
                    prefetchOfferings.clear()
                    currentOfferings.asReversed().forEach {
                        runPrefetch(it.level) {
                            it.offer.invoke()
                        }
                    }
                }

                val sortedQueue: List<Map.Entry<String, QueueElement<out IKVValue>>> = queue.asSequence().sortedByDescending { it.value.prefetchLevel }.toList()
                val regularRequestsCount = sortedQueue.lastIndex - sortedQueue.indexOfLast { it.value.prefetchLevel != 0 }
                if (regularRequestsCount == 0) break
                val prefetchRequestCount = sortedQueue.size - regularRequestsCount

                // The callback of a request usually enqueues new requests until it reaches the leafs of the
                // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                // first traversal which keeps the maximum size of the queue smaller.
                val chosenRequests = sortedQueue.tailList(regularRequestsCount.coerceIn(prefetchSize..batchSize))

                val currentRequests: List<QueueElement<out IKVValue>> = chosenRequests.map { it.value }
                chosenRequests.forEach { queue.remove(it.key) }

                if (prefetchRequestCount > prefetchQueueSizeLimit) {
                    sortedQueue.take(prefetchRequestCount - prefetchQueueSizeLimit).forEach { queue.remove(it.key) }
                }

                val entries: Map<String, IKVValue?> = executeBulkQuery(
                    currentRequests.map { obj -> obj.hash }.distinct(),
                )
                for (request in currentRequests) {
                    (request as QueueElement<IKVValue>).value.success(entries[request.hash.getHash()])
                }
            }
        } finally {
            processing = false
        }
    }

    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IBulkQuery.Value<O>): IBulkQuery.Value<List<O>> {
        val inputList = input.toList()
        if (inputList.isEmpty()) {
            return constant(emptyList())
        }
        val output = arrayOfNulls<Any>(inputList.size)
        val done = BooleanArray(inputList.size)
        var remaining = inputList.size
        val result = Value<List<O>>()
        for (i in inputList.indices) {
            f(inputList[i]).onReceive { value ->
                if (done[i]) {
                    return@onReceive
                }
                output[i] = value
                done[i] = true
                remaining--
                if (remaining == 0) {
                    result.success(output.map { e: Any? -> e as O })
                }
            }
        }
        return result
    }

    private class QueueElement<E : IKVValue>(
        val hash: KVEntryReference<E>,
        val value: Value<E?>,
        var prefetchLevel: Int
    )

    private class PrefetchOffering(
        val level: Int,
        val offer: () -> Unit
    )

    inner class Value<T> : IBulkQuery.Value<T> {
        private var handlers: Array<(T) -> Unit>? = null
        private var value: T? = null
        private var done = false

        constructor() {}
        constructor(value: T) {
            this.value = value
            done = true
        }

        @Synchronized
        fun success(value: T) {
            check(!done) { "Value is already set" }
            this.value = value
            done = true
            handlers?.forEach { it(value) }
            handlers = null
        }

        @Synchronized
        override fun onReceive(handler: (T) -> Unit) {
            if (done) {
                handler(value as T)
            } else {
                val savedPrefetchLevel = currentPrefetchLevel
                val replacedHandler: (T) -> Unit = if (savedPrefetchLevel != 0) {
                    { value : T ->
                        runPrefetch(savedPrefetchLevel) {
                            handler(value)
                        }
                    }
                } else {
                    handler
                }


                handlers = handlers.let { if (it == null) arrayOf(replacedHandler) else it + replacedHandler }
            }
        }

        override fun executeQuery(): T {
            this@BulkQuery.executeQuery()
            if (!done) {
                throw RuntimeException("No value received")
            }
            return value!!
        }

        override fun <R> map(handler: (T) -> R): IBulkQuery.Value<R> {
            val result = Value<R>()
            onReceive { v -> result.success(handler(v)) }
            return result
        }

        override fun <R> flatMap(handler: (T) -> IBulkQuery.Value<R>): IBulkQuery.Value<R> {
            val result = Value<R>()
            onReceive { v -> handler(v).onReceive { value -> result.success(value) } }
            return result
        }
    }
}

private fun <T> List<T>.tailList(n: Int): List<T> {
    if (size <= n) return this
    if (n <= 0) return emptyList()
    return subList(size - n, size)
}