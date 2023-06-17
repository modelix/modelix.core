package org.modelix.model.lazy

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.model.persistent.IKVValue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

interface IBulkQuery2 : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Companion

    suspend fun <T : IKVValue> request(hash: KVEntryReference<T>): Deferred<T>
    suspend fun flush()
    suspend fun close()

    companion object : CoroutineContext.Key<IBulkQuery2> {
        fun <T : IKVValue> requestLater(hash: KVEntryReference<T>): Flow<T> {
            return flow {
                with(getInstance()) {
                    emit(request(hash).await())
                }
            }
        }
        suspend fun getInstance(): IBulkQuery2 = coroutineContext[this]!!
        fun <T> buildBulkFlow(store: IDeserializingKeyValueStore, body: () -> Flow<T>): Flow<T> {
            return flow {
                val bulkQuery = BulkQuery2(store)
                withContext(bulkQuery) {
                    val flow = body()
                    launch {
                        bulkQuery.flush()
                    }
                    emitAll(flow)
                    bulkQuery.close()
                }
            }
        }
    }
}

private class BulkQuery2(val store: IDeserializingKeyValueStore, val maxBatchSize: Int = 5000) : IBulkQuery2 {
    private val newItems = Channel<Request<*>>(capacity = Int.MAX_VALUE)

    private val flushMutex = Mutex()

    protected fun executeBulkQuery(refs: Iterable<KVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        val refsMap = refs.associateBy { it.getHash() }
        val result = HashMap<String, IKVValue?>()
        result += refs.filter { !it.isWritten() }.map { it.getHash() to it.getValue(store) }
        val keysToQuery = refs.filter { it.isWritten() }.map { it.getHash() }
        val queriedValues = store.getAll(keysToQuery) { key, serialized -> refsMap[key]!!.getDeserializer()(serialized) }
        result += keysToQuery.zip(queriedValues)
        return result
    }

    override suspend fun close() {
        newItems.close()
    }

    override suspend fun flush() {
        flushMutex.withLock {
            var queue: List<Request<*>> = emptyList()
            while (!newItems.isClosedForReceive) {
                // The callback of a request usually enqueues new request until it reaches the leafs of the
                // data structure. By executing the newest request first we basically do a depth
                // first traversal which keeps the maximum size of the queue (and the number of coroutines) smaller.
                queue = newItems.receiveBufferedItems() + queue

                if (queue.isEmpty()) {
                    try {
                        // suspending call to receive() to wait for at least one item
                        queue = listOf(newItems.receive()) + newItems.receiveBufferedItems()
                    } catch (ex: ClosedReceiveChannelException) {
                        break
                    }
                }

                val batchSize = queue.size.coerceAtMost(maxBatchSize)
                val currentBatch: List<Request<*>> = queue.take(batchSize)
                queue = queue.drop(batchSize)
                try {
                    val entries: Map<String, IKVValue?> = executeBulkQuery(
                        currentBatch.map { obj -> obj.requestEntry }.distinct()
                    )
                    for (request in currentBatch) {
                        logExceptions {
                            val value: IKVValue? = entries[request.requestEntry.getHash()]
                            if (value == null) {
                                request.result.completeExceptionally(RuntimeException("Entry not found: " + request.requestEntry.getHash()))
                            } else {
                                (request as Request<IKVValue>).result.complete(value)
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    for (request in currentBatch) {
                        logExceptions {
                            if (!request.result.isCompleted) {
                                request.result.completeExceptionally(ex)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun <T : IKVValue> request(hash: KVEntryReference<T>): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        newItems.send(Request<T>(hash, deferred))
        return deferred
    }

    private class Request<E : IKVValue>(val requestEntry: KVEntryReference<E>, val result: CompletableDeferred<E>)
}

/**
 * The result is the same as .flattenConcat(), but each Flow is collected in a separate coroutine.
 * This allows the bulk query to collect all low level request into bigger batches.
 */
fun <T> Flow<Flow<T>>.flattenConcatConcurrent(): Flow<T> {
    val nested = this
    return flow {
        val results = Channel<Deferred<List<T>>>()
        coroutineScope {
            nested.collect { inner ->
                results.send(async { inner.toList() })
            }
        }
    }
}

fun <T, R> Flow<T>.flatMapConcatConcurrent(transform: suspend (T) -> Flow<R>): Flow<R> {
    return map(transform).flattenConcatConcurrent()
}

private fun <T> Channel<T>.receiveBufferedItems(): List<T> {
    val result = ArrayList<T>()
    while (tryReceive().onSuccess { result.add(it) }.isSuccess) {
        // handled in onSuccess
    }
    return result
}

private fun Any.logExceptions(body: () -> Unit) {
    runCatching(body).onFailure {
        mu.KotlinLogging.logger { }.error(it, { "" })
    }
}
