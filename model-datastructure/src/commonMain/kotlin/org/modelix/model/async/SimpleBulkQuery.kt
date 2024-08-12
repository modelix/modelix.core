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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.modelix.kotlin.utils.ContextValue
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue
import kotlin.coroutines.coroutineContext

class SimpleBulkQuery(val store: IDeserializingKeyValueStore) {
    companion object {
        private val contextValue = ContextValue<SimpleBulkQuery>()

        fun <T : IKVValue> query(hash: KVEntryReference<T>): Deferred<T> {
            return contextValue.getValue().query(hash)
        }

        suspend fun <R> startQuery(store: IDeserializingKeyValueStore, body: () -> Flow<R>): Flow<R> {
            val newQuery = SimpleBulkQuery(store)
            return flow<R> {
                val inputFlow = body()
                newQuery.processQueue()
                inputFlow.collect {
                    emit(it)
                    newQuery.processQueue()
                }
            }.flowOn(contextValue.getContextElement(newQuery))
        }
    }

    private val newRequests = LinkedHashMap<String, RequestedValue<*>>()

    fun <T : IKVValue> query(hash: KVEntryReference<T>): Deferred<T> {
        return runSynchronized(newRequests) {
            val request = newRequests.getOrPut(hash.getHash()) { RequestedValue<T>(hash, CompletableDeferred()) } as RequestedValue<T>
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

    private fun processQueue() {
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
}

