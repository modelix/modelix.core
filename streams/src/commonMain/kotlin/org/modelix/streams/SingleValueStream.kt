package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.modelix.kotlin.utils.DelicateModelixApi

class SingleValueStream<E>(val value: E) : IStreamInternal.One<E> {
    protected fun convertLater() = DeferredStreamBuilder.ConvertibleOne { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.One<E> {
        return converter.of(value)
    }

    override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
        return mapper(value)
    }

    override fun <R> map(mapper: (E) -> R): IStream.One<R> {
        return SingleValueStream(mapper(value))
    }

    @DelicateModelixApi
    override fun getBlocking(): E {
        return value
    }

    @DelicateModelixApi
    override suspend fun getSuspending(): E {
        return value
    }

    override fun cached(): IStream.One<E> {
        return this
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> {
        return this
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> {
        return this
    }

    override fun asFlow(): Flow<E> {
        return flowOf(value)
    }

    override fun asSequence(): Sequence<E> {
        return sequenceOf(value)
    }

    override fun toList(): IStream.One<List<E>> {
        return SingleValueStream(listOf(value))
    }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (E) -> Unit) {
        visitor(value)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
        visitor(value)
    }

    override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
        return if (predicate(value)) this else EmptyStream()
    }

    override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
        return this
    }

    override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
        return this
    }

    override fun orNull(): IStream.One<E?> {
        return this
    }

    override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
        return mapper(value)
    }

    override fun assertNotEmpty(message: () -> String): IStream.One<E> {
        return this
    }

    override fun <R> flatMapOrdered(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
        return mapper(value)
    }

    override fun <R> flatMapIterable(mapper: (E) -> Iterable<R>): IStream.Many<R> {
        return SequenceAsStream(mapper(value).asSequence())
    }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> {
        return when (other) {
            is SingleValueStream<E> -> CollectionAsStream(listOf(value, other.value))
            is SequenceAsStream<E> -> SequenceAsStream(sequenceOf(value) + other.wrapped)
            is EmptyStream<E> -> this
            is CollectionAsStream<E> -> CollectionAsStream(listOf(value) + other.collection)
            else -> convertLater().concat(other)
        }
    }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
        return when (other) {
            is SingleValueStream<E> -> CollectionAsStreamOneOrMany(listOf(value, other.value))
            is SequenceAsStreamOneOrMany<E> -> SequenceAsStreamOneOrMany(sequenceOf(value) + other.wrapped)
            is CollectionAsStreamOneOrMany<E> -> CollectionAsStreamOneOrMany(listOf(value) + other.collection)
            else -> convertLater().concat(other)
        }
    }

    override fun distinct(): IStream.OneOrMany<E> {
        return this
    }

    override fun assertEmpty(message: (E) -> String): IStream.Completable {
        throw StreamAssertionError(message(value))
    }

    override fun drainAll(): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
        return SingleValueStream(operation(initial, value))
    }

    override fun <K, V> toMap(
        keySelector: (E) -> K,
        valueSelector: (E) -> V,
    ): IStream.One<Map<K, V>> {
        return SingleValueStream(mapOf(keySelector(value) to valueSelector(value)))
    }

    override fun <R> splitMerge(
        predicate: (E) -> Boolean,
        merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
    ): IStream.Many<R> {
        return if (predicate(value)) merger(this, EmptyStream()) else merger(EmptyStream(), this)
    }

    override fun skip(count: Long): IStream.Many<E> {
        return if (count >= 1) EmptyStream() else this
    }

    override fun exactlyOne(): IStream.One<E> {
        return this
    }

    override fun count(): IStream.One<Int> {
        return SingleValueStream(1)
    }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
        return this
    }

    override fun firstOrDefault(defaultValue: E): IStream.One<E> {
        return this
    }

    override fun <T, R> zipWith(
        other: IStream.One<T>,
        mapper: (E, T) -> R,
    ): IStream.One<R> {
        return when (other) {
            is SingleValueStream<T> -> SingleValueStream(mapper(value, other.value))
            else -> convertLater().zipWith(other, mapper)
        }
    }

    override fun take(n: Int): IStream.Many<E> {
        return if (n >= 1) this else EmptyStream()
    }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
        return this
    }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
        return this
    }

    override fun isEmpty(): IStream.One<Boolean> {
        return SingleValueStream(false)
    }

    override fun withIndex(): IStream.Many<IndexedValue<E>> {
        return SingleValueStream(IndexedValue(0, value))
    }

    override fun indexOf(element: E): IStream.One<Int> {
        return SingleValueStream(if (element == value) 0 else -1)
    }
}
