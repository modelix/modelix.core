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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.flatMapConcatConcurrent
import org.modelix.kotlin.utils.print
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.kotlin.utils.toMono
import org.modelix.model.api.async.DeferredAsAsyncValue
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.api.async.MonoFlowAsAsyncValue
import org.modelix.model.api.async.asFlow
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue
import org.modelix.model.sleep

class AsyncBulkQuery(val store: IAsyncObjectStore) : IAsyncObjectStore {
//    companion object {
//        private val contextValue = ContextValue<SimpleBulkQuery>()
//
//        fun <T : IKVValue> query(hash: KVEntryReference<T>): Deferred<T> {
//            return contextValue.getValue().queryAsDeferred(hash)
//        }
//
//        suspend fun <R> runQuery(store: IDeserializingKeyValueStore, body: suspend () -> R): R {
//            val newQuery = SimpleBulkQuery(store)
//            return contextValue.runInCoroutine(newQuery) {
//                coroutineScope {
//                    val queueProcessingJob = launch {
//                        newQuery.processQueue()
//                    }
//                    val result = body()
//                    queueProcessingJob.cancel("done")
//                    result
//                }
//            }
//        }
//    }

    private val newRequests = LinkedHashMap<String, RequestedValue<*>>()
    private val pendingRequests = LinkedHashMap<String, RequestedValue<*>>()

    private fun <T : IKVValue> queryAsDeferred(hash: KVEntryReference<T>): Deferred<T> {
        store.getIfCached(hash)?.let { return CompletableDeferred(it) }
        return (runSynchronized(pendingRequests) { pendingRequests[hash.getHash()] }
            ?: runSynchronized(newRequests) {
                    val request = newRequests.getOrPut(hash.getHash()) {
                        runSynchronized(pendingRequests) {
                            pendingRequests.getOrPut(hash.getHash()) {
                                RequestedValue<T>(hash, CompletableDeferred<T>())
                            }
                        }
                    } as RequestedValue<T>
                    request
                }).deferred as Deferred<T>
    }

    override fun <T : IKVValue> getIfCached(key: IKVEntryReference<T>): T? {
        return store.getIfCached(key)
    }

    private fun copyCurrentRequests(): List<RequestedValue<*>> {
        return runSynchronized(newRequests) {
            val result = newRequests.values.toList()
            newRequests.clear()
            result
        }
    }

    private suspend fun processCurrentRequests(): Boolean {
        val requests = copyCurrentRequests()
        if (requests.isNotEmpty()) processRequests(requests)
        return requests.isNotEmpty()
    }

    private suspend fun processRequests(requests: List<RequestedValue<*>>) {
        delay(1)
        val response = store.getAll(requests.map { it.key })
        for (request in requests) {
            val value = response[request.key]
            if (value == null) {
                request.deferred.completeExceptionally(RuntimeException("Entry not found: ${request.key}"))
            } else {
                (request as RequestedValue<IKVValue>).deferred.complete(value as IKVValue)
            }
            runSynchronized(pendingRequests) {
                pendingRequests.remove(request.key.getHash())
            }
        }
    }

    private class RequestedValue<E : IKVValue>(val key: KVEntryReference<E>, val deferred: CompletableDeferred<E>)

    override fun <T : IKVValue> get(key: IKVEntryReference<T>): IAsyncValue<T?> {
        return MonoFlowAsAsyncValue(getAsFlow(key))
    }

    override suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?> {
        TODO("Not yet implemented")
    }

    override suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>) {
        TODO("Not yet implemented")
    }

    override fun <T : IKVValue> getAsFlow(key: IKVEntryReference<T>): IMonoFlow<T> {
        val deferred = queryAsDeferred(key as KVEntryReference<T>)
        return flow<T> {
            if (deferred.isCompleted) {
                emit(deferred.getCompleted())
                return@flow
            }

            coroutineScope {
                launch {
                    if (!deferred.isCompleted) processCurrentRequests()
                }
                emit(deferred.await())
            }
        }.toMono()
    }

    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IAsyncValue<O>): IAsyncValue<List<O>> {
        return requestAll(input.map(f))
    }

    override fun <T> requestAll(values: List<IAsyncValue<T>>): IAsyncValue<List<T>> {
        return MonoFlowAsAsyncValue(flow<List<T>> {
            values.asFlow().flatMapConcatConcurrent { it.asFlow() }.toList()
        }.toMono())
    }
}

