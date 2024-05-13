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
    private var processing = false
    private val batchSize: Int = batchSize ?: 5_000
    private val prefetchSize: Int = prefetchSize ?: (this.batchSize / 2)
    private val prefetchQueueSizeLimit: Int = (this.prefetchSize * 10).coerceAtLeast(this.batchSize * 2)
    private val prefetchQueue: PrefetchQueue = PrefetchQueue(this, prefetchQueueSizeLimit)

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

        val prefetchedValue = prefetchQueue.getValue(hash)
        if (prefetchedValue != null && prefetchedValue.isDone()) return prefetchedValue

        if (prefetchQueue.isLoadingGoal()) {
            val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
            val result = existingQueueElement?.value ?: prefetchedValue ?: Value()
            prefetchQueue.addRequest(hash, result)
            return result
        } else {
            if (queue.size >= batchSize && !processing) executeQuery()

            val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
            val result = if (existingQueueElement != null) {
                existingQueueElement.value
            } else {
                val result: Value<T?> = prefetchedValue ?: Value()
                queue.put(hash.getHash(), QueueElement<T>(hash, result))
                result
            }
            return result
        }
    }

    override fun offerPrefetch(goal: IPrefetchGoal) {
        prefetchQueue.addGoal(goal)
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
                if (queue.size < prefetchSize) {
                    prefetchQueue.fillRequestsQueue()
                }

                // The callback of a request usually enqueues new requests until it reaches the leafs of the
                // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                // first traversal which keeps the maximum size of the queue smaller.
                val regularRequests: List<Pair<KVEntryReference<*>, Value<*>>> = queue.entries.asSequence().map { it.value.hash to it.value.value }.toList().tailList(batchSize)
                val prefetchRequests: List<Pair<KVEntryReference<*>, Value<*>>> = prefetchQueue.getRequests { !queue.contains(it.getHash()) }.tailList(prefetchSize - regularRequests.size)
                regularRequests.forEach { queue.remove(it.first.getHash()) }

                val allRequests: List<Pair<KVEntryReference<*>, Value<*>>> = regularRequests + prefetchRequests

                val entries: Map<String, IKVValue?> = executeBulkQuery(
                    allRequests.asSequence().map { obj -> obj.first }.toSet(),
                )
                for (request in allRequests) {
                    (request.second as Value<IKVValue?>).success(entries[request.first.getHash()])
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
    )

    inner class Value<T> : IBulkQuery.Value<T> {
        private var handlers: MutableList<(T) -> Unit>? = null
        private var value: T? = null
        private var done = false

        constructor() {}
        constructor(value: T) {
            this.value = value
            done = true
        }

        fun isDone() = done

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
                if (handlers == null) handlers = ArrayList(1)
                handlers!!.add(handler)
                check(handlers.let { it == null || it.size < 1_000 }) {
                    "Too many handlers"
                }
            }
        }

        override fun executeQuery(): T {
            this@BulkQuery.executeQuery()
            if (!done) {
                throw RuntimeException("No value received")
            }
            return value!!
        }

        override fun <R> map(transformation: (T) -> R): IBulkQuery.Value<R> {
            val result = Value<R>()
            onReceive { v -> result.success(transformation(v)) }
            return result
        }

        override fun <R> flatMap(transformation: (T) -> IBulkQuery.Value<R>): IBulkQuery.Value<R> {
            val result = Value<R>()
            onReceive { v -> transformation(v).onReceive { value -> result.success(value) } }
            return result
        }
    }
}

private fun <T> List<T>.tailList(n: Int): List<T> {
    if (size <= n) return this
    if (n <= 0) return emptyList()
    return subList(size - n, size)
}

interface IPrefetchGoal {
    fun executeRequests(bulkQuery: IBulkQuery)
}

class PrefetchQueue(val bulkQuery: IBulkQuery, val queueSizeLimit: Int) {
    private val goals: MutableSet<IPrefetchGoal> = LinkedHashSet()
    private val requests: MutableMap<String, PrefetchRequest<*>> = LinkedHashMap()
    private var currentGoal: IPrefetchGoal? = null
    private var anyEntryRequested = false

    fun isLoadingGoal() = currentGoal != null

    fun fillRequestsQueue() {
        for (goal in goals.toList().asReversed()) {
            executeRequests(goal)
        }
    }

    fun getRequests(condition: (KVEntryReference<*>) -> Boolean): List<Pair<KVEntryReference<*>, BulkQuery.Value<*>>> {
        return requests.values.asSequence()
            .filterNot { it.result.isDone() }
            .filter { condition(it.hash) }
            .map { it.hash to it.result }
            .toList()
    }

    fun addGoal(goal: IPrefetchGoal) {
        // remove and re-add to move it to the end of the queue
        goals.remove(goal)
        goals.add(goal)
        trimQueue()
    }

    fun <T : IKVValue> addRequest(hash: KVEntryReference<T>, result: BulkQuery.Value<T?>) {
        addRequest(hash, checkNotNull(currentGoal) { "Not loading any goal" }, result)
    }

    private fun <T : IKVValue> addRequest(hash: KVEntryReference<T>, goal: IPrefetchGoal, result: BulkQuery.Value<T?>) {
        anyEntryRequested = true

        // remove and re-add the request to put it at the end of the queue
        val request = requests.remove(hash.getHash())?.also { require(result == it.result) }
            ?: PrefetchRequest(hash, result)
        request.contributesToGoals.add(goal)
        requests[hash.getHash()] = request
        trimQueue()
    }

    fun <T : IKVValue> getValue(hash: KVEntryReference<T>): BulkQuery.Value<T?>? {
        return (requests[hash.getHash()] as PrefetchRequest<T>?)?.result
    }

    private fun trimQueue() {
        while (requests.size > queueSizeLimit) {
            requests.remove(requests.keys.first())
        }
        while (goals.size > queueSizeLimit) {
            goals.remove(goals.first())
        }
    }

    /**
     * A prefetch goal can be loading a node with a specific ID.
     * On the path to the node data, several other objects need to be loaded first.
     * If there are many available goals we may have to stop working on an old goal, which is totally valid because
     * its probably just a wrong prediction of what is actually needed.
     *
     * Some of the objects on the path to the goal may already be evicted from the cache before reaching the goal and need
     * to be requested again, as long the goal is active.
     */
    fun executeRequests(goal: IPrefetchGoal) {
        val previousGoal = currentGoal
        val previousAnyEntryRequested = anyEntryRequested
        try {
            currentGoal = goal
            anyEntryRequested = false
            goal.executeRequests(bulkQuery)
            if (!anyEntryRequested) {
                goals.remove(goal)
            }
        } finally {
            anyEntryRequested = previousAnyEntryRequested
            currentGoal = previousGoal
        }
    }

    private inner class PrefetchRequest<E : IKVValue>(val hash: KVEntryReference<E>, val result: BulkQuery.Value<E?>) {
        init {
            result.onReceive {
                contributesToGoals.forEach {
                    executeRequests(it)
                }
            }
        }
        val contributesToGoals: MutableSet<IPrefetchGoal> = HashSet()
    }
}

