/*
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
package org.modelix.kotlin.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * The result is the same as .flattenConcat(), but each Flow is collected in a separate coroutine.
 * This allows the bulk query to collect all low level request into bigger batches.
 */
fun <T> Flow<Flow<T>>.flattenConcatConcurrent(): Flow<T> {
    val nested = this
    return flow {
        val results = Channel<Deferred<List<T>>>()
        coroutineScope {
            launch {
                nested.collect { inner ->
                    results.send(async { inner.toList() })
                }
                results.close()
            }
            for (result in results) {
                val list = result.await()
                for (item in list) {
                    emit(item)
                }
            }
        }
    }
}

fun <T, R> Flow<T>.flatMapConcatConcurrent(transform: suspend (T) -> Flow<R>): Flow<R> {
    return map(transform).flattenConcatConcurrent()
}
