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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
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
        coroutineScope {
            val results = Channel<Deferred<List<T>>>()
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

interface IOptionalMonoFlow<out E> : Flow<E>
interface IMonoFlow<out E> : IOptionalMonoFlow<E>
class MonoFlow<out E>(
    private val input: Flow<E>,
    private val messageIfEmpty: () -> String = { "Flow didn't return any elements" },
    private val messageIfMultiple: () -> String = { "Flow returned more than one element" },
): IMonoFlow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        var elementCount = 0
        input.collect {
            elementCount++
            check(elementCount <= 1, messageIfMultiple)
            collector.emit(it)
        }
        check(elementCount == 1, messageIfEmpty)
    }
}
class ConstantMonoFlow<E>(val value: E): IMonoFlow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        collector.emit(value)
    }
}
class NullIfEmptyMonoFlow<out E>(private val input: IOptionalMonoFlow<E>) : IMonoFlow<E?> {
    override suspend fun collect(collector: FlowCollector<E?>) {
        var elementCount = 0
        input.collect {
            elementCount++
            collector.emit(it)
        }
        if (elementCount == 0) collector.emit(null)
    }
}
class OptionalMonoFlow<out E>(
    private val input: Flow<E>,
    private val messageIfMultiple: () -> String = { "Flow returned more than one element" }
): IOptionalMonoFlow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {
        var elementCount = 0
        input.collect {
            elementCount++
            check(elementCount <= 1, messageIfMultiple)
            collector.emit(it)
        }
    }
}
class EmptyMonoFlow<out E> : IOptionalMonoFlow<E> {
    override suspend fun collect(collector: FlowCollector<E>) {}
}

fun <T> emptyMonoFlow(): IOptionalMonoFlow<T> = EmptyMonoFlow()
fun <T : Any> monoFlowOf(value: T): IMonoFlow<T> = ConstantMonoFlow(value)
fun <T> nullableMonoFlowOf(value: T): IMonoFlow<T> = ConstantMonoFlow(value)
fun <T : Any> optionalMonoFlowOf(value: T?): IOptionalMonoFlow<T> = if (value == null) emptyMonoFlow<T>() else monoFlowOf(value)
fun <T> monoFlow(value: suspend () -> T): IMonoFlow<T> = flow<T> { emit(value()) }.toMono()
fun <T> Flow<T>.toMono(): IMonoFlow<T> = MonoFlow<T>(this)
fun <T> Flow<T>.toOptionalMono(): IOptionalMonoFlow<T> = OptionalMonoFlow<T>(this)
fun <T> IOptionalMonoFlow<T>.orNull(): IMonoFlow<T?> = NullIfEmptyMonoFlow(this)
fun <T> IOptionalMonoFlow<T>.checkNotEmpty(message: () -> String): IMonoFlow<T> {
    return MonoFlow(this, messageIfEmpty = message)
}
fun <T : Any> IOptionalMonoFlow<T?>.filterNotNull(): IOptionalMonoFlow<T> = OptionalMonoFlow((this as Flow<T?>).filterNotNull())
fun <T : Any> IMonoFlow<T?>.checkNotNull(message: () -> String): IMonoFlow<T> = MonoFlow((this as Flow<T?>).filterNotNull(), messageIfEmpty = message)
fun <In, Out> IMonoFlow<In>.mapValue(transform: suspend (In) -> Out): IMonoFlow<Out> = map(transform).toMono()
fun <In, Out> IMonoFlow<In>.mapMono(transform: suspend (In) -> IMonoFlow<Out>): IMonoFlow<Out> {
    return flatMapConcat(transform).toMono()
}
fun <In, Out> IMonoFlow<In>.flatMap(transform: suspend (In) -> Flow<Out>): Flow<Out> {
    return flatMapConcat(transform)
}
fun <In, Out> IOptionalMonoFlow<In>.mapMono(transform: suspend (In) -> IOptionalMonoFlow<Out>): IOptionalMonoFlow<Out> {
    return flatMapConcat(transform).toOptionalMono()
}
fun <In, Out> IOptionalMonoFlow<In>.mapValue(transform: suspend (In) -> Out): IOptionalMonoFlow<Out> {
    return map(transform).toOptionalMono()
}
