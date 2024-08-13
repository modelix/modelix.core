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
package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.toList

fun <T, R> Flow<T>.batchTransform(batchSize: Int, transform: (List<T>) -> List<R>): Flow<R> {
    return chunked(batchSize).flatMapConcat { transform(it).asFlow() }
}

fun <T> Flow<T>.chunked(chunkSize: Int): Flow<List<T>> {
    val input = this
    return flow<List<T>> {
        var list = ArrayList<T>(chunkSize)
        input.collect {
            if (list.size < chunkSize) {
                list.add(it)
            } else {
                emit(list)
                list = ArrayList<T>(chunkSize)
            }
        }
        if (list.isNotEmpty()) emit(list)
    }
}

/**
 * Like .single(), but also allows an empty input.
 */
suspend fun <T> Flow<T>.optionalSingle(): Optional<T> {
    var result = Optional.empty<T>()
    collect {
        require(!result.isPresent()) { "Didn't expect multiple elements" }
        result = Optional.of(it)
    }
    return result
}

fun <T> Flow<T>.assertNotEmpty(additionalMessage: () -> String = { "" }): Flow<T> {
    return onEmpty { throw IllegalArgumentException("At least one element was expected. " + additionalMessage()) }
}

suspend fun <T> Flow<T>.asSequence(): Sequence<T> = toList().asSequence()
