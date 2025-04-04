package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.modelix.kotlin.utils.DelicateModelixApi

class EmptyStream<E> : IStream.ZeroOrOne<E> {
    override fun convert(converter: IStreamBuilder): IStream.ZeroOrOne<E> {
        return converter.empty()
    }

    override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
        return this
    }

    override fun <R> map(mapper: (E) -> R): IStream.ZeroOrOne<R> {
        return EmptyStream()
    }

    override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
        return SingleValueStream(defaultValue())
    }

    override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
        throw exception()
    }

    override fun orNull(): IStream.One<E?> {
        return SingleValueStream(null)
    }

    override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
        return EmptyStream()
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.ZeroOrOne<E> {
        return this
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.ZeroOrOne<E> {
        return this
    }

    override fun assertNotEmpty(message: () -> String): IStream.One<E> {
        throw StreamAssertionError(message())
    }

    override fun asFlow(): Flow<E> {
        return emptyFlow()
    }

    override fun asSequence(): Sequence<E> {
        return asSequence()
    }

    override fun toList(): IStream.One<List<E>> {
        return SingleValueStream(emptyList())
    }

    @DelicateModelixApi
    override fun iterateSynchronous(visitor: (E) -> Unit) {
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
    }

    override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
        return EmptyStream()
    }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> {
        return other
    }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
        return other
    }

    override fun distinct(): IStream.Many<E> {
        return this
    }

    override fun assertEmpty(message: (E) -> String): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun drainAll(): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
        return SingleValueStream(initial)
    }

    override fun <K, V> toMap(
        keySelector: (E) -> K,
        valueSelector: (E) -> V,
    ): IStream.One<Map<K, V>> {
        return SingleValueStream(emptyMap())
    }

    override fun <R> splitMerge(
        predicate: (E) -> Boolean,
        merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
    ): IStream.Many<R> {
        return merger(this, this)
    }

    override fun skip(count: Long): IStream.Many<E> {
        return this
    }

    override fun exactlyOne(): IStream.One<E> {
        throw NoSuchElementException("Empty stream")
    }

    override fun count(): IStream.One<Int> {
        return SingleValueStream(0)
    }

    override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
        return this
    }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
        return SingleValueStream(defaultValue())
    }

    override fun take(n: Int): IStream.Many<E> {
        return this
    }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
        return this
    }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
        return alternative()
    }

    override fun isEmpty(): IStream.One<Boolean> {
        return SingleValueStream(true)
    }

    override fun withIndex(): IStream.Many<IndexedValue<E>> {
        return EmptyStream()
    }
}
