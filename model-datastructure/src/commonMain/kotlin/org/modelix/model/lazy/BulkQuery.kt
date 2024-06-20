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

/**
 * Not thread safe
 */
class BulkQuery(private val store: IDeserializingKeyValueStore, config: BulkQueryConfiguration) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private var processing = false
    private val batchSize: Int = config.requestBatchSize
    private val prefetchSize: Int = config.prefetchBatchSize ?: (this.batchSize / 2)
    private val prefetchQueueSizeLimit: Int = (this.prefetchSize * 10).coerceAtLeast(this.batchSize * 2)
    private val prefetchQueue: PrefetchQueue = PrefetchQueue(this, prefetchQueueSizeLimit).also {
        it.requestFilter = { !queue.contains(it.getHash()) }
    }

    init {
        require(this.prefetchSize <= this.batchSize) { "prefetch size ${this.prefetchSize} is greater than the batch size ${this.batchSize}" }
    }

    private fun <T : IKVValue> getValueInstance(ref: KVEntryReference<T>): Value<T>? {
        return queue[ref.getHash()]?.let { it.value as Value<T>? }
            ?: (prefetchQueue.getValueInstance(ref) as Value<T>?)
    }

    protected fun executeBulkQuery(regular: List<IKVEntryReference<IKVValue>>, prefetch: List<IKVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        return store.getAll(regular, prefetch)
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IBulkQuery.Value<T?> {
        if (!hash.isWritten()) return constant(hash.getValue(store))

        val cachedValue = store.getIfCached(hash.getHash(), hash.getDeserializer(), prefetchQueue.isLoadingGoal())
        if (cachedValue != null) {
            return constant(cachedValue)
        }

        val existingValue = getValueInstance(hash)
        if (existingValue != null && existingValue.isDone()) return existingValue

        if (prefetchQueue.isLoadingGoal()) {
            prefetchQueue.addRequest(hash, getValueInstance(hash) as Value<T?>? ?: Value())
            return DummyValue() // transitive objects are loaded when the prefetch queue is processed the next time
        } else {
            if (queue.size >= batchSize && !processing) executeQuery()

            val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
            val result = if (existingQueueElement != null) {
                existingQueueElement.value
            } else {
                val result: Value<T?> = getValueInstance(hash) as Value<T?>? ?: Value()
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
                // The callback of a request usually enqueues new requests until it reaches the leafs of the
                // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                // first traversal which keeps the maximum size of the queue smaller.
                val regularRequests: List<Pair<KVEntryReference<*>, Value<*>>> = queue.entries.tailSequence(batchSize)
                    .map { it.value.hash to it.value.value }
                    .toList()
                if (queue.size < prefetchSize) {
                    prefetchQueue.fillRequestsQueue(prefetchSize - regularRequests.size)
                }
                val prefetchRequests: List<Pair<KVEntryReference<*>, Value<*>>> = prefetchQueue.getRequests(prefetchSize - regularRequests.size)
                regularRequests.forEach { queue.remove(it.first.getHash()) }

                val allRequests: List<Pair<KVEntryReference<*>, Value<*>>> = regularRequests + prefetchRequests

                val entries: Map<String, IKVValue?> = executeBulkQuery(
                    regularRequests.asSequence().map { obj -> obj.first }.toSet().toList(),
                    prefetchRequests.asSequence().map { obj -> obj.first }.toSet().toList(),
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

        fun success(value: T) {
            check(!done) { "Value is already set" }
            this.value = value
            done = true
            handlers?.forEach { it(value) }
            handlers = null
        }

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

    class DummyValue<E> : IBulkQuery.Value<E> {
        override fun executeQuery(): E {
            throw UnsupportedOperationException()
        }

        override fun <R> flatMap(handler: (E) -> IBulkQuery.Value<R>): IBulkQuery.Value<R> = DummyValue()

        override fun <R> map(handler: (E) -> R): IBulkQuery.Value<R> = DummyValue()

        override fun onReceive(handler: (E) -> Unit) {}
    }
}

private fun <T> Sequence<T>.tailSequence(size: Int, tailSize: Int): Sequence<T> {
    if (size <= tailSize) return this
    if (tailSize <= 0) return emptySequence()
    return drop(size - tailSize)
}

private fun <T> Collection<T>.tailSequence(tailSize: Int): Sequence<T> {
    return asSequence().tailSequence(size, tailSize)
}

interface IPrefetchGoal {
    fun loadRequest(bulkQuery: IBulkQuery)
}

private class PrefetchQueue(val bulkQuery: IBulkQuery, val queueSizeLimit: Int) {
    private val goals: MutableMap<IPrefetchGoal, QueuedGoal> = LinkedHashMap()
    private var previousRequests: MutableMap<String, PrefetchRequest<*>> = LinkedHashMap()
    private var nextRequests: MutableMap<String, PrefetchRequest<*>> = LinkedHashMap()
    private var currentGoal: QueuedGoal? = null
    private var anyEntryRequested = false
    var requestFilter: (KVEntryReference<*>) -> Boolean = { true }

    fun isLoadingGoal() = currentGoal != null

    fun fillRequestsQueue(requestLimit: Int) {
        if (requestLimit <= 0) return

        previousRequests = nextRequests
        nextRequests = LinkedHashMap()

        for (goal in goals.values.toList().sortedByDescending { it.prefetchLevel }.asReversed()) {
            if (nextRequests.size >= requestLimit) break
            executeRequests(goal)
        }
    }

    fun getRequests(limit: Int): List<Pair<KVEntryReference<*>, BulkQuery.Value<*>>> {
        return nextRequests.entries.tailSequence(limit)
            .map { it.value.hash to it.value.result }
            .toList()
    }

    fun addGoal(goal: IPrefetchGoal) {
        val newLevel = currentGoal?.prefetchLevel?.let { it + 1 } ?: 0
        // remove and re-add to move it to the end of the queue
        val queuedGoal = goals.remove(goal)?.also { it.prefetchLevel = minOf(it.prefetchLevel, newLevel) } ?: QueuedGoal(goal, newLevel)
        goals[goal] = queuedGoal
        trimQueue()
    }

    fun <T : IKVValue> addRequest(hash: KVEntryReference<T>, result: BulkQuery.Value<T?>) {
        addRequest(hash, checkNotNull(currentGoal) { "Not loading any goal" }, result)
    }

    private fun <T : IKVValue> addRequest(hash: KVEntryReference<T>, goal: QueuedGoal, result: BulkQuery.Value<T?>) {
        anyEntryRequested = true

        val request = (previousRequests[hash.getHash()] ?: nextRequests[hash.getHash()])?.also {
            require(result == it.result)
            it.prefetchLevel = minOf(it.prefetchLevel, goal.prefetchLevel)
        } ?: PrefetchRequest(hash, result, goal.prefetchLevel)

        if (!request.result.isDone() && requestFilter(request.hash)) {
            nextRequests[hash.getHash()] = request
        }
        trimQueue()
    }

    fun <T : IKVValue> getValueInstance(hash: KVEntryReference<T>): BulkQuery.Value<T?>? {
        return ((nextRequests[hash.getHash()] ?: previousRequests[hash.getHash()]) as PrefetchRequest<T>?)?.result
    }

    private fun trimQueue() {
        if (goals.size > queueSizeLimit * 2) {
            val toRemove = goals.entries.sortedBy { it.value.prefetchLevel }.drop(goals.size - queueSizeLimit).map { it.key }
            toRemove.forEach { goals.remove(it) }
        }
    }

    private fun executeRequests(goal: QueuedGoal) {
        val previousGoal = currentGoal
        val previousAnyEntryRequested = anyEntryRequested
        try {
            currentGoal = goal
            anyEntryRequested = false
            goal.goal.loadRequest(bulkQuery)
            if (!anyEntryRequested) {
                goals.remove(goal.goal)
            }
        } finally {
            anyEntryRequested = previousAnyEntryRequested
            currentGoal = previousGoal
        }
    }

    private inner class QueuedGoal(val goal: IPrefetchGoal, var prefetchLevel: Int)

    private inner class PrefetchRequest<E : IKVValue>(val hash: KVEntryReference<E>, val result: BulkQuery.Value<E?>, var prefetchLevel: Int)
}
