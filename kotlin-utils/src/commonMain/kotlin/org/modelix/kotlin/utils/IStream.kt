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

package org.modelix.kotlin.utils

import kotlinx.coroutines.flow.Flow

interface IStreamFactory {
    fun <T> lazyConstant(provider: () -> T): IMonoStream<T>
    fun <T> constant(provider: T): IMonoStream<T>
    fun <T> fromIterable(input: Iterable<T>): IStream<T>
    fun <T> ifEmpty(stream: IStream<T>, alternative: () -> IStream<T>): IStream<T>
    fun <T> ifEmpty(stream: IOptionalMonoStream<T>, alternative: () -> IMonoStream<T>): IMonoStream<T>
    fun <T> flatten(streams: Iterable<IStream<T>>): IStream<T>
    fun <T> empty(): IOptionalMonoStream<T>
    fun <T> zip(streams: List<IStream<T>>): IStream<List<T>>
}

interface IStream<out E> {
    suspend fun toSequence(): Sequence<E>
    fun toSequenceBlocking(): Sequence<E>
    fun asFlow(): Flow<E>
    fun getFactory(): IStreamFactory
    fun <R> map(transform: (E) -> R): IStream<R>
    fun <R> flatMapConcat(transform: (E) -> IStream<R>): IStream<R>
    fun cached(): IStream<E>
    fun toList(): IMonoStream<List<E>>
    fun toSet(): IMonoStream<Set<E>>
    fun count(): IMonoStream<Int>
    fun drop(count: Int): IStream<E>
    fun filter(predicate: (E) -> Boolean): IStream<E>
    fun filterByMono(predicate: (E) -> IMonoStream<Boolean>): IStream<E>
    fun filterNotNull(): IStream<E & Any>
    fun optionalSingle(): IOptionalMonoStream<E>
    fun single(): IMonoStream<E>
    fun take(count: Int): IStream<E>
    fun firstOrNull(): IMonoStream<E?>
    fun first(): IOptionalMonoStream<E>
    fun isEmpty(): IMonoStream<Boolean>
    fun assertNotEmpty(message: () -> String): IStream<E>
    fun withIndex(): IStream<IndexedValue<E>>
    fun <T> fold(initial: T, f: (acc: T, value: E) -> T): IMonoStream<T>
}

fun <T> IStream<T>.ifEmptyThenStream(alternative: () -> IStream<T>): IStream<T> = getFactory().ifEmpty(this, alternative)
fun <T> IStream<T>.ifEmpty(alternative: () -> T): IStream<T> = getFactory().ifEmpty(this, { getFactory().constant(alternative()) })
fun <T> IOptionalMonoStream<T>.ifEmpty(alternative: () -> T): IMonoStream<T> = getFactory().ifEmpty(this, { getFactory().constant(alternative()) })

fun <T> IStream<IStream<T>>.flatten() = flatMapConcat { it }
operator fun <T> IStream<T>.plus(other: IStream<T>): IStream<T> = getFactory().flatten(listOf(this, other))
operator fun <T> IStream<T>.plus(others: Iterable<IStream<T>>): IStream<T> = getFactory().flatten(listOf(this) + others)

interface IOptionalMonoStream<out E> : IStream<E> {
    fun presentAndEqual(value: Any?): IMonoStream<Boolean>
    override fun assertNotEmpty(message: () -> String): IMonoStream<E>
    override fun <R> map(transform: (E) -> R): IOptionalMonoStream<R>
    override fun cached(): IOptionalMonoStream<E>
}

interface IMonoStream<out E> : IOptionalMonoStream<E> {
    suspend fun getValue(): E
    override fun <R> map(transform: (E) -> R): IMonoStream<R>
    override fun filterNotNull(): IOptionalMonoStream<E & Any>
    override fun cached(): IMonoStream<E>
}
