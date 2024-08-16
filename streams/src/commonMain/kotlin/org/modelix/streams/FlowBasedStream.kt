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

package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.flow.withIndex

class FlowBasedStreamFactory(val coroutineScope: CoroutineScope?) : IStreamFactory {
    private fun <T> Flow<T>.asStream() = FlowBasedStream(this, this@FlowBasedStreamFactory)
    private fun <T> Flow<T>.asMonoStream() = FlowBasedMonoStream(this, this@FlowBasedStreamFactory)
    private fun <T> Flow<T>.asOptionalMonoStream() = FlowBasedOptionalMonoStream(this, this@FlowBasedStreamFactory)

    override fun <T> fromIterable(input: Iterable<T>): IStream<T> {
        return input.asFlow().asStream()
    }

    override fun <T> lazyConstant(provider: () -> T): IMonoStream<T> {
        return flow {
            emit(provider())
        }.asMonoStream()
    }

    override fun <T> zip(streams: List<IStream<T>>): IStream<List<T>> {
        // TODO this might be slower, but `combine` seems to be buggy (elements get lost)
        return flow<List<T>> {
            for (element in CombiningSequence(streams.map { it.toSequence() }.toTypedArray())) {
                emit(element)
            }
        }.asStream()
    }

    override fun <T> constant(value: T): IMonoStream<T> {
        return flowOf(value).asMonoStream()
    }

    override fun <T> ifEmpty(stream: IStream<T>, alternative: () -> IStream<T>): IStream<T> {
        return stream.asFlow().onEmpty { emitAll(alternative().asFlow()) }.asStream()
    }

    override fun <T> flatten(streams: Iterable<IStream<T>>): IStream<T> {
        return streams.asFlow().flatMapConcat { it.asFlow() }.asStream()
    }

    override fun <T> empty(): IOptionalMonoStream<T> {
        return emptyFlow<T>().asOptionalMonoStream()
    }

    override fun <T> ifEmpty(stream: IOptionalMonoStream<T>, alternative: () -> IMonoStream<T>): IMonoStream<T> {
        return stream.asFlow().onEmpty { emitAll(alternative().asFlow()) }.asMonoStream()
    }
}

open class FlowBasedStream<E>(val flow: Flow<E>, private val factory: FlowBasedStreamFactory) : IStream<E> {
    fun <T> Flow<T>.asStream() = FlowBasedStream(this, factory)
    fun <T> Flow<T>.asMonoStream() = FlowBasedMonoStream(this, factory)
    fun <T> Flow<T>.asOptionalMonoStream() = FlowBasedOptionalMonoStream(this, factory)
    fun <T> IStream<T>.asFlow() = (this as FlowBasedStream).flow


    override fun <R> flatMapConcat(transform: (E) -> IStream<R>): IStream<R> {
        return flow.flatMapConcat { transform(it).asFlow() }.asStream()
    }

    override fun <R> map(transform: (E) -> R): IStream<R> {
        return flow.map(transform).asStream()
    }

    override fun cached(): IStream<E> {
        val scope = factory.coroutineScope ?: throw RuntimeException("Coroutine scope required for caching of $this")
        return flow.shareIn(scope, SharingStarted.Lazily, 1)
            .take(1) // The shared flow seems to ignore that there are no more elements and keeps the subscribers active.
            .asStream()
    }

    override fun getFactory(): IStreamFactory {
        return factory
    }

    override fun toList(): IMonoStream<List<E>> {
        return flow { emit(flow.toList()) }.asMonoStream()
    }

    override fun toSet(): IMonoStream<Set<E>> {
        return flow { emit(flow.toSet()) }.asMonoStream()
    }

    override fun count(): IMonoStream<Int> {
        return flow { emit(flow.count()) }.asMonoStream()
    }

    override fun isEmpty(): IMonoStream<Boolean> {
        return flow.take(1).map { false }.onEmpty { emit(true) }.asMonoStream()
    }

    override suspend fun toSequence(): Sequence<E> {
        return flow.toList().asSequence()
    }

    override fun toSequenceBlocking(): Sequence<E> {
        throw UnsupportedOperationException()
    }

    override fun asFlow(): Flow<E> {
        return flow
    }

    override fun drop(count: Int): IStream<E> {
        return flow.drop(count).asStream()
    }

    override fun filter(predicate: (E) -> Boolean): IStream<E> {
        return flow.filter(predicate).asStream()
    }

    override fun filterByMono(predicate: (E) -> IMonoStream<Boolean>): IStream<E> {
        return flow.filter { predicate(it).getValue() }.asStream()
    }

    override fun filterNotNull(): IStream<E & Any> {
        return flow.filter { it != null }.asStream() as IStream<E & Any>
    }

    override fun optionalSingle(): IOptionalMonoStream<E> {
        return flow<E> {
            var elementCount = 0
            flow.collect {
                elementCount++
                check(elementCount <= 1) { "Flow has more than one element" }
                emit(it)
            }
        }.asOptionalMonoStream()
    }

    override fun single(): IMonoStream<E> {
        return flow<E> {
            var elementCount = 0
            flow.collect {
                elementCount++
                check(elementCount <= 1) { "Flow has more than one element" }
                emit(it)
            }
            check(elementCount == 1) { "Flow didn't return any elements" }
        }.asMonoStream()
    }

    override fun take(count: Int): IStream<E> {
        return flow.take(count).asStream()
    }

    override fun firstOrNull(): IMonoStream<E?> {
        return flow.take(1).onEmpty<E?> { emit(null) }.asMonoStream()
    }

    override fun first(): IOptionalMonoStream<E> {
        return flow.take(1).asOptionalMonoStream()
    }

    override fun assertNotEmpty(message: () -> String): IStream<E> {
        return flow.assertNotEmpty(message).asStream()
    }

    override fun withIndex(): IStream<IndexedValue<E>> {
        return flow.withIndex().asStream()
    }

    override fun <T> fold(initial: T, f: (acc: T, value: E) -> T): IMonoStream<T> {
        return flow {
            emit(flow.fold(initial, f))
        }.asMonoStream()
    }
}

open class FlowBasedOptionalMonoStream<E>(flow: Flow<E>, factory: FlowBasedStreamFactory) : FlowBasedStream<E>(flow, factory), IOptionalMonoStream<E> {
    override fun assertNotEmpty(message: () -> String): IMonoStream<E> {
        return flow.assertNotEmpty(message).asMonoStream()
    }

    override fun presentAndEqual(value: Any?): IMonoStream<Boolean> {
        return flow.take(1).map { it == value }.onEmpty { emit(false) }.asMonoStream()
    }

    override fun <R> map(transform: (E) -> R): IOptionalMonoStream<R> {
        return flow.map(transform).asOptionalMonoStream()
    }

    override fun cached(): IOptionalMonoStream<E> {
        return super.cached().asFlow().asOptionalMonoStream()
    }
}

class FlowBasedMonoStream<E>(flow: Flow<E>, factory: FlowBasedStreamFactory) : FlowBasedOptionalMonoStream<E>(flow, factory), IMonoStream<E> {
    override fun filterNotNull(): IOptionalMonoStream<E & Any> {
        return flow.filter { it != null }.asOptionalMonoStream() as IOptionalMonoStream<E & Any>
    }

    override fun <R> map(transform: (E) -> R): IMonoStream<R> {
        return flow.map(transform).asMonoStream()
    }

    override suspend fun getValue(): E {
        return flow.single()
    }

    override fun cached(): IMonoStream<E> {
        return super.cached().asFlow().asMonoStream()
    }
}

class CombiningSequence<Common>(private val sequences: Array<Sequence<Common>>) : Sequence<List<Common>> {
    override fun iterator(): Iterator<List<Common>> = object : Iterator<List<Common>> {
        var initialized = false
        val lastValues = Array<Any?>(sequences.size) { UNINITIALIZED }
        val iterators = sequences.map { it.iterator() }.toTypedArray()
        override fun next(): List<Common> {
            for (i in sequences.indices) {
                if (iterators[i].hasNext()) lastValues[i] = iterators[i].next()
            }
            initialized = true
            return lastValues.map { it as Common }
        }

        override fun hasNext(): Boolean {
            return if (initialized) iterators.any { it.hasNext() } else iterators.all { it.hasNext() }
        }
    }
    object UNINITIALIZED
}

fun <T> Flow<T>.assertNotEmpty(additionalMessage: () -> String = { "" }): Flow<T> {
    return onEmpty { throw IllegalArgumentException("At least one element was expected. " + additionalMessage()) }
}
