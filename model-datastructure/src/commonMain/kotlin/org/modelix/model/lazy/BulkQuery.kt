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

/**
 * Not thread safe
 */
class BulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery {
    companion object {
        val BATCH_SIZE = 5000
    }

    private var queue: MutableList<Pair<KVEntryReference<IKVValue>, (IKVValue?) -> Unit>> = ArrayList()
    private var processing = false
    protected fun executeBulkQuery(refs: Iterable<KVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        val refsMap = refs.associateBy { it.getHash() }
        val result = HashMap<String, IKVValue?>()
        result += refs.filter { !it.isWritten() }.map { it.getHash() to it.getValue(store) }
        val keysToQuery = refs.filter { it.isWritten() }.map { it.getHash() }
        val queriedValues = store.getAll(keysToQuery) { key, serialized -> refsMap[key]!!.getDeserializer()(serialized) }
        result += keysToQuery.zip(queriedValues)
        return result
    }

    fun <T : IKVValue> query(key: KVEntryReference<T>, callback: (T) -> Unit) {
        if (queue.size >= BATCH_SIZE && !processing) executeQuery()
        queue.add(Pair(key as KVEntryReference<IKVValue>, callback as (IKVValue?) -> Unit))
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IBulkQuery.Value<T?> {
        val result = Value<T?>()
        query(hash) { value: T? -> result.success(value) }
        return result
    }

    override fun <T> constant(value: T): IBulkQuery.Value<T> {
        return Value(value)
    }

    override fun executeQuery() {
        if (processing) {
            throw RuntimeException("Already processing")
        }
        processing = true
        try {
            while (queue.isNotEmpty()) {
                val currentRequests: List<Pair<KVEntryReference<IKVValue>, (IKVValue?) -> Unit>>
                if (queue.size > BATCH_SIZE) {
                    // The callback of a request usually enqueues new request until it reaches the leafs of the
                    // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                    // first traversal which keeps the maximum size of the queue smaller.
                    currentRequests = ArrayList(queue.subList(queue.size - BATCH_SIZE, queue.size))
                    for (i in 1..BATCH_SIZE) queue.removeLast()
                } else {
                    currentRequests = queue
                    queue = ArrayList()
                }
                val entries: Map<String, IKVValue?> = executeBulkQuery(
                    currentRequests.map { obj -> obj.first }.distinct(),
                )
                for (request in currentRequests) {
                    request.second(entries[request.first.getHash()])
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

    inner class Value<T> : IBulkQuery.Value<T> {
        private var handlers: MutableList<(T) -> Unit>? = ArrayList()
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
            for (handler in handlers!!) {
                handler(value)
            }
            handlers = null
        }

        @Synchronized
        override fun onReceive(handler: (T) -> Unit) {
            if (done) {
                handler(value as T)
            } else {
                handlers!!.add(handler)
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
