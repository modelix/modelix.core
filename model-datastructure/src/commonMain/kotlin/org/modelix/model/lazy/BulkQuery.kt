package org.modelix.model.lazy

import com.badoo.reaktive.single.notNull
import kotlinx.coroutines.sync.Mutex
import org.modelix.kotlin.utils.AtomicBoolean
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.CompletableObservable
import org.modelix.streams.IStream
import org.modelix.streams.ReaktiveStreamBuilder

/**
 * Not thread safe
 */
@Deprecated("use IAsyncStore")
class BulkQuery(private val store: IDeserializingKeyValueStore, config: BulkQueryConfiguration) : IBulkQuery {
    private val queue: MutableMap<String, QueueElement<out IKVValue>> = LinkedHashMap()
    private var processing = AtomicBoolean(false)
    private val batchSize: Int = config.requestBatchSize
    private val processingMutex = Mutex()

    private fun <T : IKVValue> getValueInstance(ref: IKVEntryReference<T>): CompletableObservable<T?>? {
        return queue[ref.getHash()]?.let { it.value as CompletableObservable<T?>? }
    }

    private fun executeBulkQuery(regular: List<IKVEntryReference<IKVValue>>): Map<String, IKVValue?> {
        return store.getAll(regular)
    }

    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T> {
        if (!hash.isWritten()) return IStream.of(hash.getValue(store))

        val cachedValue = store.getIfCached(hash.getHash(), hash.getDeserializer(), false)
        if (cachedValue != null) {
            return IStream.of(cachedValue)
        }

        val existingValue = getValueInstance(hash)
        if (existingValue != null && existingValue.isDone()) return ReaktiveStreamBuilder.WrapperMaybe(existingValue.single.notNull())

        if (queue.size >= batchSize && !processing.get()) executeQuery()

        val existingQueueElement = queue[hash.getHash()] as QueueElement<T>?
        val result = if (existingQueueElement != null) {
            existingQueueElement.value
        } else {
            val result: CompletableObservable<T?> = getValueInstance(hash) ?: CompletableObservable(::executeQuery)
            queue.put(hash.getHash(), QueueElement<T>(hash, result))
            result
        }
        return ReaktiveStreamBuilder.WrapperMaybe(result.single.notNull())
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
                    regularRequests.forEach { queue.remove(it.first.getHash()) }

                    val allRequests: List<Pair<IKVEntryReference<*>, CompletableObservable<*>>> = regularRequests

                    val entries: Map<String, IKVValue?> = executeBulkQuery(
                        regularRequests.asSequence().map { obj -> obj.first }.toSet().toList(),
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
