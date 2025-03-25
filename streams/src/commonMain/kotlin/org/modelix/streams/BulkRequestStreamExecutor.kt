package org.modelix.streams

import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.notNull
import com.badoo.reaktive.single.subscribe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.yield
import org.modelix.kotlin.utils.ContextValue
import org.modelix.kotlin.utils.runSynchronized

interface IBulkExecutor<K, V> {
    fun execute(keys: List<K>): Map<K, V>
    suspend fun executeSuspending(keys: List<K>): Map<K, V>
}

class BulkRequestStreamExecutor<K, V>(private val bulkExecutor: IBulkExecutor<K, V>, val batchSize: Int = 5000) : IStreamExecutor, IStreamExecutorProvider {
    private val requestQueue = ContextValue<RequestQueue>()
    private val streamBuilder = ReaktiveStreamBuilder(this)

    private inner class RequestQueue {
        val queue: MutableMap<K, QueueElement> = LinkedHashMap()

        fun process() {
            while (queue.isNotEmpty()) {
                sendNextBatch { bulkExecutor.execute(it) }
            }
        }

        suspend fun processSuspending(afterBatch: suspend () -> Unit) {
            while (queue.isNotEmpty()) {
                sendNextBatch { bulkExecutor.executeSuspending(it) }
                // Give stream consumers time to process the new elements.
                yield()
                afterBatch()
            }
        }

        private inline fun sendNextBatch(executor: (keys: List<K>) -> Map<K, V>) {
            val requests = runSynchronized(queue) {
                // The callback of a request usually enqueues new requests until it reaches the leafs of the
                // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                // first traversal which keeps the maximum size of the queue smaller.
                val requests = queue.entries.tailSequence(batchSize).map { it.value.hash to it.value }.toList()
                requests.forEach { queue.remove(it.first) }
                requests
            }
            if (requests.isEmpty()) return
            try {
                val map = executor(requests.map { it.first })
                for ((_, queueElement) in requests) {
                    queueElement
                    val value = map[queueElement.hash]
                    queueElement.requestResult.complete(value)
                }
            } catch (ex: Throwable) {
                for ((_, queueElement) in requests) {
                    queueElement.requestResult.failed(ex)
                }
            }
        }

        fun query(hash: K): IStream.ZeroOrOne<V> {
            return (queue.getOrPut(hash) { QueueElement(hash, queue.size) }).value.notNull()
                .let { streamBuilder.WrapperMaybe(it) }
        }
    }

    private inner class QueueElement(val hash: K, val position: Int) {
        val requestResult = CompletableObservable<V?>()
        val value: Single<V?> = requestResult.single
    }

    override fun getStreamExecutor(): IStreamExecutor = this

    fun enqueue(key: K): IStream.ZeroOrOne<V> {
        return requestQueue.getValue().query(key)
    }

    override fun <T> query(body: () -> IStream.One<T>): T {
        fun doProcess(queue: RequestQueue): T {
            var result: Result<T>? = null
            val reaktiveStream = streamBuilder.convert(body())
            val subscription = reaktiveStream.subscribe(
                onSuccess = { result = Result.success(it) },
                onError = { result = Result.failure(it) },
            )
            try {
                queue.process()
            } finally {
                subscription.dispose()
            }
            return checkNotNull(result) { "Empty stream" }.getOrThrow()
        }

        val existingQueue = requestQueue.getValueOrNull()
        return if (existingQueue == null) {
            val newQueue = RequestQueue()
            requestQueue.computeWith(newQueue) {
                IStream.useBuilder(streamBuilder) {
                    doProcess(newQueue)
                }
            }
        } else {
            doProcess(existingQueue)
        }
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        suspend fun doProcess(queue: RequestQueue): T {
            var result: Result<T>? = null
            val reaktiveStream = streamBuilder.convert(body())
            val subscription = reaktiveStream.subscribe(
                onSuccess = { result = Result.success(it) },
                onError = { result = Result.failure(it) },
            )
            try {
                queue.processSuspending({})
            } finally {
                subscription.dispose()
            }
            return checkNotNull(result) { "Empty stream" }.getOrThrow()
        }
        val existingQueue = requestQueue.getValueOrNull()
        return if (existingQueue == null) {
            val newQueue = RequestQueue()
            requestQueue.runInCoroutine(newQueue) {
                IStream.useBuilderSuspending(streamBuilder) {
                    doProcess(newQueue)
                }
            }
        } else {
            doProcess(existingQueue)
        }
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        fun doProcess(queue: RequestQueue) {
            val reaktiveStream = streamBuilder.convert(streamProvider())
            val subscription = reaktiveStream.subscribe(onNext = visitor)
            try {
                queue.process()
            } finally {
                subscription.dispose()
            }
        }
        val existingQueue = requestQueue.getValueOrNull()
        return if (existingQueue == null) {
            val newQueue = RequestQueue()
            requestQueue.computeWith(newQueue) {
                IStream.useBuilder(streamBuilder) {
                    doProcess(newQueue)
                }
            }
        } else {
            doProcess(existingQueue)
        }
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        suspend fun doProcess(queue: RequestQueue) {
            val reaktiveStream = streamBuilder.convert(streamProvider())
            val channel = Channel<T>(capacity = Channel.Factory.UNLIMITED)
            val subscription = reaktiveStream.subscribe(
                onNext = { channel.trySend(it).getOrThrow() },
            )
            try {
                suspend fun drainChannel() {
                    while (channel.tryReceive().onSuccess { visitor(it) }.isSuccess) {
                        // element already processed in onSuccess
                    }
                }

                queue.processSuspending({
                    // force processing of elements after each batch to avoid the channel from growing to big
                    drainChannel()
                })
                drainChannel()
            } finally {
                subscription.dispose()
            }
        }

        val existingQueue = requestQueue.getValueOrNull()
        return if (existingQueue == null) {
            val newQueue = RequestQueue()
            requestQueue.runInCoroutine(newQueue) {
                IStream.useBuilderSuspending(streamBuilder) {
                    doProcess(newQueue)
                }
            }
        } else {
            doProcess(existingQueue)
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
