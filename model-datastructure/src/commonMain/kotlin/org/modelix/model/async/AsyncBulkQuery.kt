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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.kotlin.utils.toMono
import org.modelix.model.api.async.DeferredAsAsyncValue
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.api.async.MonoFlowAsAsyncValue
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

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
    private val processMutex = Mutex()

    fun <T : IKVValue> queryAsDeferred(hash: KVEntryReference<T>): Deferred<T> {
        store.getIfCached(hash)?.let { return CompletableDeferred(it) }
        return runSynchronized(newRequests) {
            val request = newRequests.getOrPut(hash.getHash()) { RequestedValue<T>(hash, CompletableDeferred()) } as RequestedValue<T>
            request.deferred
        }
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

    private suspend fun processCurrentRequests() {
        processMutex.withLock {
            val requests = copyCurrentRequests()
            processRequests(requests)
        }
    }

    private suspend fun processRequests(requests: List<RequestedValue<*>>) {
        val response = store.getAll(requests.map { it.key })
        for (request in requests) {
            val value = response[request.key]
            if (value == null) {
                request.deferred.completeExceptionally(RuntimeException("Entry not found: ${request.key}"))
            } else {
                (request as RequestedValue<IKVValue>).deferred.complete(value as IKVValue)
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
            if (!deferred.isCompleted) processCurrentRequests()
            emit(deferred.getCompleted())
        }.toMono()
    }
}

