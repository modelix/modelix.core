/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.async

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.modelix.kotlin.utils.ContextValue
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.model.api.async.DeferredAsAsyncValue
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.api.async.NonAsyncValue
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.IPrefetchGoal
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

class SimpleBulkQuery(val store: IDeserializingKeyValueStore) : IBulkQuery {
    companion object {
        private val contextValue = ContextValue<SimpleBulkQuery>()

        fun <T : IKVValue> query(hash: KVEntryReference<T>): Deferred<T> {
            return contextValue.getValue().queryAsDeferred(hash)
        }

        suspend fun <R> runQuery(store: IDeserializingKeyValueStore, body: suspend () -> R): R {
            val newQuery = SimpleBulkQuery(store)
            return contextValue.runInCoroutine(newQuery) {
                coroutineScope {
                    val queueProcessingJob = launch {
                        newQuery.processQueue()
                    }
                    val result = body()
                    queueProcessingJob.cancel("done")
                    result
                }
            }
        }
    }

    private val newRequests = LinkedHashMap<String, RequestedValue<*>>()
    private val queueTrigger = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

    suspend fun processQueue() {
        processCurrentQueue()
        for (x in queueTrigger) {
            processCurrentQueue()
        }
    }

    fun <T : IKVValue> queryAsDeferred(hash: KVEntryReference<T>): Deferred<T> {
        store.getIfCached(hash.getHash(), hash.getDeserializer(), false)?.let { return CompletableDeferred(it) }
        return runSynchronized(newRequests) {
            val request = newRequests.getOrPut(hash.getHash()) { RequestedValue<T>(hash, CompletableDeferred()) } as RequestedValue<T>
            queueTrigger.trySend(Unit)
            request.deferred
        }
    }

    private fun copyCurrentRequests(): Map<String, RequestedValue<*>> {
        return runSynchronized(newRequests) {
            val result = newRequests.toMap()
            newRequests.clear()
            result
        }
    }

    private fun processCurrentQueue() {
        var requests = copyCurrentRequests()
        while (requests.isNotEmpty()) {
            processRequests(requests)
            requests = copyCurrentRequests()
        }
     }

    private fun processRequests(requests: Map<String, RequestedValue<*>>) {
        val entries = store.getAll(requests.values.map { it.key }, emptyList())
        for (entry in entries) {
            val request = (requests[entry.key] ?: continue) as RequestedValue<IKVValue>
            val value = entry.value
            if (value == null) {
                request.deferred.completeExceptionally(NullPointerException("Entry not found: ${request.key.getHash()}"))
            } else {
                request.deferred.complete(value)
            }
        }
    }

    private class RequestedValue<E : IKVValue>(val key: KVEntryReference<E>, val deferred: CompletableDeferred<E>)

    override fun <T> constant(value: T): IAsyncValue<T> {
        return NonAsyncValue(value)
    }

    override fun offerPrefetch(key: IPrefetchGoal) {
        TODO("Not yet implemented")
    }

    override fun executeQuery() {
        TODO("Not yet implemented")
    }

    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IAsyncValue<O>): IAsyncValue<List<O>> {
        TODO("Not yet implemented")
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IAsyncValue<T?> {
        return DeferredAsAsyncValue(queryAsDeferred(hash))
    }
}

