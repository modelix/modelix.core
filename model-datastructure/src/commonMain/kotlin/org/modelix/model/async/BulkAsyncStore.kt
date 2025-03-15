package org.modelix.model.async

import com.badoo.reaktive.maybe.doOnAfterSubscribe
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.notNull
import org.modelix.kotlin.utils.ThreadLocal
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.CompletableObservable
import org.modelix.streams.IStream
import org.modelix.streams.ReaktiveStreamBuilder
import org.modelix.streams.SynchronousPipeline

class BulkAsyncStore(val store: IAsyncObjectStore) : IAsyncObjectStore {
    private val threadLocalData: ThreadLocal<ThreadLocalData> = ThreadLocal { ThreadLocalData() }
    private val batchSize: Int = 5000

    private inner class ThreadLocalData {
        val queue: MutableMap<ObjectHash<*>, QueueElement<out Any>> = LinkedHashMap()
        val triggerFunction = ::triggerProcessing

        fun triggerProcessing() {
            while (queue.isNotEmpty()) {
                sendNextBatch()
            }
        }

        private fun sendNextBatch() {
            val requests = runSynchronized(queue) {
                // The callback of a request usually enqueues new requests until it reaches the leafs of the
                // data structure. By executing the latest (instead of the oldest) request we basically do a depth
                // first traversal which keeps the maximum size of the queue smaller.
                val requests = queue.entries.tailSequence(batchSize).map { it.value.hash to it.value }.toList()
                requests.forEach { queue.remove(it.first) }
                requests
            }
            if (requests.isEmpty()) return
            val currentRequest = store.getAllAsMap(requests.map { it.first })
            currentRequest.getAsync(
                onError = { ex ->
                    for ((_, queueElement) in requests) {
                        queueElement.requestResult.failed(ex)
                    }
                },
                onSuccess = { map ->
                    for ((_, queueElement) in requests) {
                        queueElement as QueueElement<Any>
                        val value = map[queueElement.hash]
                        queueElement.requestResult.complete(value)
                    }
                },
            )
        }

        fun <T : Any> query(hash: ObjectHash<T>): IStream.ZeroOrOne<T> {
            val cachedValue = store.getIfCached(hash)
            if (cachedValue != null) {
                return IStream.of(cachedValue)
            }

            return (queue.getOrPut(hash) { QueueElement(hash, queue.size) } as QueueElement<T>).value.notNull().doOnAfterSubscribe {
                val pipeline = SynchronousPipeline.contextValue.getValueOrNull()
                if (pipeline == null) {
                    triggerProcessing()
                } else {
                    if (!pipeline.afterRootSubscribed.contains(triggerFunction)) {
                        pipeline.afterRootSubscribed.add(triggerFunction)
                    }
                }
            }.let { ReaktiveStreamBuilder.WrapperMaybe(it) }
        }
    }

    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.getLegacyKeyValueStore()
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    private inner class QueueElement<E : Any>(val hash: ObjectHash<E>, val position: Int) {
        val requestResult = CompletableObservable<E?>()
        val value: Single<E?> = requestResult.single
    }

    override fun <T : Any> get(key: ObjectHash<T>): IStream.ZeroOrOne<T> {
        return threadLocalData.get().query(key)
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return store.getIfCached(key)
    }

    override fun getAllAsStream(keys: IStream.Many<ObjectHash<*>>): IStream.Many<Pair<ObjectHash<*>, Any?>> {
        return keys.flatMap { key -> get(key).orNull().map { key to it } }
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): IStream.One<Map<ObjectHash<*>, Any?>> {
        return getAllAsStream(IStream.many(keys)).toMap({ it.first }, { it.second })
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): IStream.Zero {
        return store.putAll(entries)
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
