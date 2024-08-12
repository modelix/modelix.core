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

package org.modelix.model.api.async

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat

class AsyncValueAsFlow<E>(val value: IAsyncValue<E>) : Flow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        collector.emit(value.await())
    }
}

fun <T> IAsyncValue<T>.asFlow() = AsyncValueAsFlow(this)

class AsyncIterableAsFlow<E>(val value: IAsyncValue<Iterable<E>>) : Flow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        for (element in value.await()) {
            collector.emit(element)
        }
    }
}

fun <T> IAsyncValue<Iterable<T>>.asFlattenedFlow(): Flow<T> = this.asFlow().flatMapConcat { it.asFlow() } // AsyncIterableAsFlow(this)
