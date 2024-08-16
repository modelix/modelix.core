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

import kotlinx.coroutines.flow.Flow

class SequenceBasedStream<out E> : IStream<E> {
    override fun asFlow(): Flow<E> {
        TODO("Not yet implemented")
    }

    override suspend fun toSequence(): Sequence<E> {
        TODO("Not yet implemented")
    }

    override fun toSequenceBlocking(): Sequence<E> {
        TODO("Not yet implemented")
    }

    override fun getFactory(): IStreamFactory {
        TODO("Not yet implemented")
    }

    override fun <R> map(transform: (E) -> R): IStream<R> {
        TODO("Not yet implemented")
    }

    override fun <R> flatMapConcat(transform: (E) -> IStream<R>): IStream<R> {
        TODO("Not yet implemented")
    }

    override fun cached(): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun toList(): IMonoStream<List<E>> {
        TODO("Not yet implemented")
    }

    override fun toSet(): IMonoStream<Set<E>> {
        TODO("Not yet implemented")
    }

    override fun count(): IMonoStream<Int> {
        TODO("Not yet implemented")
    }

    override fun drop(count: Int): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun filter(predicate: (E) -> Boolean): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun filterByMono(predicate: (E) -> IMonoStream<Boolean>): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun filterNotNull(): IStream<E & Any> {
        TODO("Not yet implemented")
    }

    override fun optionalSingle(): IOptionalMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun single(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun take(count: Int): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun firstOrNull(): IMonoStream<E?> {
        TODO("Not yet implemented")
    }

    override fun first(): IOptionalMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): IMonoStream<Boolean> {
        TODO("Not yet implemented")
    }

    override fun assertNotEmpty(message: () -> String): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun withIndex(): IStream<IndexedValue<E>> {
        TODO("Not yet implemented")
    }

    override fun <T> fold(initial: T, f: (acc: T, value: E) -> T): IMonoStream<T> {
        TODO("Not yet implemented")
    }
}

class SimpleMonoStream<E>(val value: E, private val factory: IStreamFactory) : IMonoStream<E> {
    override fun asFlow(): Flow<E> {
        TODO("Not yet implemented")
    }

    override suspend fun getValue(): E = value

    override fun <R> map(transform: (E) -> R): IMonoStream<R> {
        return SimpleMonoStream(transform(value), factory)
    }

    override fun filterNotNull(): IOptionalMonoStream<E & Any> {
        TODO("Not yet implemented")
    }

    override fun cached(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun presentAndEqual(value: Any?): IMonoStream<Boolean> {
        TODO("Not yet implemented")
    }

    override fun assertNotEmpty(message: () -> String): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override suspend fun toSequence(): Sequence<E> {
        TODO("Not yet implemented")
    }

    override fun toSequenceBlocking(): Sequence<E> {
        TODO("Not yet implemented")
    }

    override fun getFactory(): IStreamFactory = factory

    override fun <R> flatMapConcat(transform: (E) -> IStream<R>): IStream<R> {
        return transform(value)
    }

    override fun toList(): IMonoStream<List<E>> {
        TODO("Not yet implemented")
    }

    override fun toSet(): IMonoStream<Set<E>> {
        TODO("Not yet implemented")
    }

    override fun count(): IMonoStream<Int> {
        TODO("Not yet implemented")
    }

    override fun drop(count: Int): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun filter(predicate: (E) -> Boolean): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun filterByMono(predicate: (E) -> IMonoStream<Boolean>): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun optionalSingle(): IOptionalMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun single(): IMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun take(count: Int): IStream<E> {
        TODO("Not yet implemented")
    }

    override fun firstOrNull(): IMonoStream<E?> {
        TODO("Not yet implemented")
    }

    override fun first(): IOptionalMonoStream<E> {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): IMonoStream<Boolean> {
        TODO("Not yet implemented")
    }

    override fun withIndex(): IStream<IndexedValue<E>> {
        TODO("Not yet implemented")
    }

    override fun <T> fold(initial: T, f: (acc: T, value: E) -> T): IMonoStream<T> {
        TODO("Not yet implemented")
    }
}
