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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.kotlin.utils.AtomicBoolean
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.streams.IMonoStream
import org.modelix.kotlin.utils.toMono
import org.modelix.model.api.async.AsyncSequence
import org.modelix.model.api.async.AsyncValueAsStream
import org.modelix.model.api.async.IAsyncSequence
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.async.BulkQueryAsStreamFactory
import org.modelix.model.persistent.IKVValue

/**
 * Not thread safe
 */
class BulkQuery(private val store: IDeserializingKeyValueStore, config: BulkQueryConfiguration) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private var processing = AtomicBoolean(false)
    private val processingLock = Any()
    private val batchSize: Int = config.requestBatchSize
    private val prefetchSize: Int = config.prefetchBatchSize ?: (this.batchSize / 2)
    private val prefetchQueueSizeLimit: Int = (this.prefetchSize * 10).coerceAtLeast(this.batchSize * 2)
    private val prefetchQueue: PrefetchQueue = PrefetchQueue(this, prefetchQueueSizeLimit).also {
        it.requestFilter = { !queue.contains(it.getHash()) }
    }
    private val processingMutex = Mutex()

    init {
        require(this.prefetchSize <= this.batchSize) { "prefetch size ${this.prefetchSize} is greater than the batch size ${this.batchSize}" }
    }

    private fun <T : IKVValue> getValueInstance(ref: KVEntryReference<T>): Value<T>? {
        return queue[ref.getHash()]?.let { it.value as Value<T>? }
            ?: (prefetchQueue.getValueInstance(ref) as Value<T>?)
    }

    private fun executeBulkQuery(regular: List<IKVEntryReference<IKVValue>>, prefetch: List<IKVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        return store.getAll(regular, prefetch)
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IAsyncValue<T?> {
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
            if (queue.size >= batchSize && !processing.get()) executeQuery()

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

    override fun <T> constant(value: T): IAsyncValue<T> {
        return Value(value)
    }

    private suspend fun executeQuerySuspending() {
        processingMutex.withLock {
            executeQuery()
        }
    }

    override fun executeQuery() {
        if (processing.compareAndSet(false, true)) {
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
                processing.set(false)
            }
        } else {
            throw RuntimeException("Already processing")
        }
    }

    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IAsyncValue<O>): IAsyncValue<List<O>> {
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

    inner class Value<T> : IAsyncValue<T> {
        private var value: CompletableDeferred<T>

        constructor() {
            value = CompletableDeferred()
        }
        constructor(value: T) {
            this.value = CompletableDeferred(value)
        }

        override fun asStream(): IMonoStream<T> {
            return AsyncValueAsStream(this, BulkQueryAsStreamFactory(this@BulkQuery))
        }

        fun isDone() = value.isCompleted

        fun success(value: T) {
            check(!isDone()) { "Value is already set" }
            this.value.complete(value)
        }

        override fun onReceive(callback: (T) -> Unit) {
            value.invokeOnCompletion {
                callback(value.getCompleted())
            }
        }

        override fun <R> map(body: (T) -> R): IAsyncValue<R> {
            val result = Value<R>()
            onReceive { v -> result.success(body(v)) }
            return result
        }

        override fun <R> thenRequest(handler: (T) -> IAsyncValue<R>): IAsyncValue<R> {
            val result = Value<R>()
            onReceive { v -> handler(v).onReceive { value -> result.success(value) } }
            return result
        }

        override suspend fun await(): T {
            if (value.isCompleted) return value.getCompleted()
            executeQuerySuspending()
            return value.await()
        }

        override fun awaitBlocking(): T {
            this@BulkQuery.executeQuery()
            if (!isDone()) {
                throw RuntimeException("No value received")
            }
            return value.getCompleted()
        }

        override fun asFlow(): IMonoFlow<T> {
            if (value.isCompleted) return flowOf(value.getCompleted()).toMono()
            return flow<T> {
                if (!value.isCompleted) {
                    executeQuerySuspending()
                }
                emit(value.getCompleted())
            }.toMono()
        }

        override fun <R> flatMap(body: (T) -> Iterable<R>): IAsyncSequence<R> {
            return AsyncSequence(map { body(it).asSequence() }, BulkQueryAsStreamFactory(this@BulkQuery))
        }
    }

    class DummyValue<E> : IAsyncValue<E> {
        override fun asStream(): IMonoStream<E> {
            TODO("Not yet implemented")
        }

        override fun <R> thenRequest(handler: (E) -> IAsyncValue<R>): IAsyncValue<R> = DummyValue()

        override fun <R> map(handler: (E) -> R): IAsyncValue<R> = DummyValue()

        override fun onReceive(handler: (E) -> Unit) {}

        override suspend fun await(): E {
            throw UnsupportedOperationException()
        }

        override fun awaitBlocking(): E {
            throw UnsupportedOperationException()
        }

        override fun asFlow(): IMonoFlow<E> {
            throw UnsupportedOperationException()
        }

        override fun <R> flatMap(body: (E) -> Iterable<R>): IAsyncSequence<R> {
            TODO("Not yet implemented")
        }
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
