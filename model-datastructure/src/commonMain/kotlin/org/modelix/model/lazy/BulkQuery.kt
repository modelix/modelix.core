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
class BulkQuery(private val store: IDeserializingKeyValueStore, val batchSize: Int = 5_000) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private var prefetchOfferings: MutableList<() -> Unit> = ArrayList()
    private var processing = false
    private var prefetchMode = false
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
            if (existingQueueElement.isPrefetch && !prefetchMode) {
                existingQueueElement.isPrefetch = false
            }
            return existingQueueElement.value
        }

        val result = Value<T?>()
        queue.put(hash.getHash(), QueueElement<T>(hash, result, prefetchMode))
        return result
    }

    override fun offerPrefetch(body: () -> Unit) {
        prefetchOfferings.add(body)
    }

    override fun <T> constant(value: T): IBulkQuery.Value<T> {
        return Value(value)
    }

    private fun runPrefetch(body: () -> Unit) {
        if (prefetchMode) {
            body()
            return
        }
        prefetchMode = true
        try {
            body()
        } finally {
            prefetchMode = false
        }
    }

    override fun executeQuery() {
        if (processing) {
            throw RuntimeException("Already processing")
        }
        processing = true
        try {
            while (queue.isNotEmpty()) {
                val regularRequests: List<Map.Entry<String, QueueElement<out IKVValue>>> = queue.asSequence().filter { !it.value.isPrefetch }.toList()
                if (regularRequests.isEmpty()) break

                while (queue.size < batchSize && prefetchOfferings.isNotEmpty()) {
                    runPrefetch {
                        for (i in prefetchOfferings.indices.reversed()) {
                            if (queue.size >= batchSize) break
                            prefetchOfferings[i].invoke()
                            prefetchOfferings.removeAt(i)
                        }
                    }
                }

                val chosenRequests: List<Map.Entry<String, QueueElement<out IKVValue>>>
                if (regularRequests.size >= batchSize) {
                    // The callback of a request usually enqueues new requests until it reaches the leafs of the
                    // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                    // first traversal which keeps the maximum size of the queue smaller.
                    chosenRequests = regularRequests.tailList(batchSize)
                } else {
                    val prefetchRequests = queue.asSequence().filter { it.value.isPrefetch }.toList()
                    chosenRequests = regularRequests + prefetchRequests.tailList(batchSize - regularRequests.size)
                }

                val currentRequests: List<QueueElement<out IKVValue>> = chosenRequests.map { it.value }
                chosenRequests.forEach { queue.remove(it.key) }

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
        var isPrefetch: Boolean
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
                val replacedHandler: (T) -> Unit = if (prefetchMode) {
                    { value : T ->
                        runPrefetch {
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