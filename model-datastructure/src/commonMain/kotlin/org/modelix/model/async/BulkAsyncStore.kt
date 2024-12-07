package org.modelix.model.async

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.doOnAfterSubscribe
import com.badoo.reaktive.maybe.toMaybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.toMap
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.notNull
import com.badoo.reaktive.single.subscribe
import org.modelix.kotlin.utils.ThreadLocal
import org.modelix.model.IKeyValueStore
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.CompletableObservable
import org.modelix.streams.SynchronousPipeline
import org.modelix.streams.orNull

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
            currentRequest.subscribe(onSubscribe = {
            }, onError = { ex ->
                for ((_, queueElement) in requests) {
                    queueElement.requestResult.failed(ex)
                }
            }, onSuccess = { map ->
                for ((_, queueElement) in requests) {
                    queueElement as QueueElement<Any>
                    val value = map[queueElement.hash]
                    queueElement.requestResult.complete(value)
                }
            })
        }

        fun <T : Any> query(hash: ObjectHash<T>): Maybe<T> {
            val cachedValue = store.getIfCached(hash)
            if (cachedValue != null) {
                return cachedValue.toMaybe()
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
            }
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

    override fun <T : Any> get(key: ObjectHash<T>): Maybe<T> {
        return threadLocalData.get().query(key)
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return store.getIfCached(key)
    }

    override fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>> {
        return keys.flatMapSingle { key -> get(key).orNull().map { key to it } }
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>> {
        return getAllAsStream(keys.asObservable()).toMap({ it.first }, { it.second })
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable {
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
