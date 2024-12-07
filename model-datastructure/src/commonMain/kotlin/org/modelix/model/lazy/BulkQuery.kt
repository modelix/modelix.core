package org.modelix.model.lazy

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.toMaybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.single.notNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.kotlin.utils.AtomicBoolean
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.CompletableObservable

/**
 * Not thread safe
 */
@Deprecated("use IAsyncStore")
class BulkQuery(private val store: IDeserializingKeyValueStore, config: BulkQueryConfiguration) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private var processing = AtomicBoolean(false)
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

    private fun <T : IKVValue> getValueInstance(ref: IKVEntryReference<T>): CompletableObservable<T?>? {
        return queue[ref.getHash()]?.let { it.value as CompletableObservable<T?>? }
            ?: (prefetchQueue.getValueInstance(ref) as CompletableObservable<T?>?)
    }

    private fun executeBulkQuery(regular: List<IKVEntryReference<IKVValue>>, prefetch: List<IKVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        return store.getAll(regular, prefetch)
    }

    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): Maybe<T> {
        if (!hash.isWritten()) return hash.getValue(store).toMaybe()

        val cachedValue = store.getIfCached(hash.getHash(), hash.getDeserializer(), prefetchQueue.isLoadingGoal())
        if (cachedValue != null) {
            return cachedValue.toMaybe()
        }

        val existingValue = getValueInstance(hash)
        if (existingValue != null && existingValue.isDone()) return existingValue.single.notNull()

        if (prefetchQueue.isLoadingGoal()) {
            prefetchQueue.addRequest(hash, getValueInstance(hash) ?: CompletableObservable(::executeQuery))
            return maybeOfEmpty() // transitive objects are loaded when the prefetch queue is processed the next time
        } else {
            if (queue.size >= batchSize && !processing.get()) executeQuery()

            val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
            val result = if (existingQueueElement != null) {
                existingQueueElement.value
            } else {
                val result: CompletableObservable<T?> = getValueInstance(hash) ?: CompletableObservable(::executeQuery)
                queue.put(hash.getHash(), QueueElement<T>(hash, result))
                result
            }
            return result.single.notNull()
        }
    }

    override fun offerPrefetch(goal: IPrefetchGoal) {
        prefetchQueue.addGoal(goal)
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
                    val regularRequests: List<Pair<IKVEntryReference<*>, CompletableObservable<*>>> = queue.entries.tailSequence(batchSize)
                        .map { it.value.hash to it.value.value }
                        .toList()
                    if (queue.size < prefetchSize) {
                        prefetchQueue.fillRequestsQueue(prefetchSize - regularRequests.size)
                    }
                    val prefetchRequests: List<Pair<IKVEntryReference<*>, CompletableObservable<*>>> = prefetchQueue.getRequests(prefetchSize - regularRequests.size)
                    regularRequests.forEach { queue.remove(it.first.getHash()) }

                    val allRequests: List<Pair<IKVEntryReference<*>, CompletableObservable<*>>> = regularRequests + prefetchRequests

                    val entries: Map<String, IKVValue?> = executeBulkQuery(
                        regularRequests.asSequence().map { obj -> obj.first }.toSet().toList(),
                        prefetchRequests.asSequence().map { obj -> obj.first }.toSet().toList(),
                    )
                    for (request in allRequests) {
                        (request.second as CompletableObservable<IKVValue?>).complete(entries[request.first.getHash()])
                    }
                }
            } finally {
                processing.set(false)
            }
        } else {
            // throw RuntimeException("Already processing")
        }
    }

    private class QueueElement<E : IKVValue>(
        val hash: IKVEntryReference<E>,
        val value: CompletableObservable<E?>,
    )
}

private fun <T> Sequence<T>.tailSequence(size: Int, tailSize: Int): Sequence<T> {
    if (size <= tailSize) return this
    if (tailSize <= 0) return emptySequence()
    return drop(size - tailSize)
}

private fun <T> Collection<T>.tailSequence(tailSize: Int): Sequence<T> {
    return asSequence().tailSequence(size, tailSize)
}

@Deprecated("Prefetching will be replaced by usages of IAsyncNode")
interface IPrefetchGoal {
    fun loadRequest(bulkQuery: IBulkQuery): Observable<Any?>
}

@Deprecated("Prefetching will be replaced by usages of IAsyncNode")
private class PrefetchQueue(val bulkQuery: IBulkQuery, val queueSizeLimit: Int) {
    private val goals: MutableMap<IPrefetchGoal, QueuedGoal> = LinkedHashMap()
    private var previousRequests: MutableMap<String, PrefetchRequest<*>> = LinkedHashMap()
    private var nextRequests: MutableMap<String, PrefetchRequest<*>> = LinkedHashMap()
    private var currentGoal: QueuedGoal? = null
    private var anyEntryRequested = false
    var requestFilter: (IKVEntryReference<*>) -> Boolean = { true }

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

    fun getRequests(limit: Int): List<Pair<IKVEntryReference<*>, CompletableObservable<*>>> {
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

    fun <T : IKVValue> addRequest(hash: IKVEntryReference<T>, result: CompletableObservable<T?>) {
        addRequest(hash, checkNotNull(currentGoal) { "Not loading any goal" }, result)
    }

    private fun <T : IKVValue> addRequest(hash: IKVEntryReference<T>, goal: QueuedGoal, result: CompletableObservable<T?>) {
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

    fun <T : IKVValue> getValueInstance(hash: IKVEntryReference<T>): CompletableObservable<T?>? {
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
            goal.goal.loadRequest(bulkQuery).subscribe(onSubscribe = {}, onNext = {}, onError = {}, onComplete = {})
            if (!anyEntryRequested) {
                goals.remove(goal.goal)
            }
        } finally {
            anyEntryRequested = previousAnyEntryRequested
            currentGoal = previousGoal
        }
    }

    private inner class QueuedGoal(val goal: IPrefetchGoal, var prefetchLevel: Int)

    private inner class PrefetchRequest<E : IKVValue>(val hash: IKVEntryReference<E>, val result: CompletableObservable<E?>, var prefetchLevel: Int)
}
