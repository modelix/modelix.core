package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.modelix.kotlin.utils.DelicateModelixApi

/**
 * Runs operations eagerly, if they return a single value.
 * If an operation returns many values, it remains a lazy sequence to avoid unnecessary memory consumption.
 */
open class SequenceAsStream<E>(val wrapped: Sequence<E>) : IStream.Many<E>, IStreamInternal<E> {
    protected open fun convertLater() = DeferredStreamBuilder.ConvertibleMany { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.Many<E> {
        return converter.many(wrapped)
    }

    override fun <R> map(mapper: (E) -> R): IStream.Many<R> {
        return SequenceAsStream(wrapped.map(mapper))
    }

    override fun asFlow(): Flow<E> {
        return wrapped.asFlow()
    }

    override fun asSequence(): Sequence<E> {
        return wrapped
    }

    override fun toList(): IStream.One<List<E>> {
        return SingleValueStream(wrapped.toList())
    }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (E) -> Unit) {
        wrapped.forEach(visitor)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
        wrapped.forEach { visitor(it) }
    }

    override fun filter(predicate: (E) -> Boolean): IStream.Many<E> {
        return SequenceAsStream(wrapped.filter(predicate))
    }

    override fun ifEmpty_(alternative: () -> E): IStream.OneOrMany<E> {
        return SequenceAsStreamOneOrMany(wrapped.ifEmpty { sequenceOf(alternative()) })
    }

    override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
        return convertLater().flatMap(mapper)
    }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> {
        return convertLater().concat(other)
    }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
        return convertLater().concat(other)
    }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
        return SingleValueStream(wrapped.fold(initial, operation))
    }

    override fun distinct(): IStream.Many<E> {
        return SequenceAsStream(wrapped.distinct())
    }

    override fun assertEmpty(message: (E) -> String): IStream.Completable {
        wrapped.forEach { throw StreamAssertionError(message(it)) }
        return EmptyCompletableStream()
    }

    override fun drainAll(): IStream.Completable {
        wrapped.forEach {}
        return EmptyCompletableStream()
    }

    override fun <K, V> toMap(
        keySelector: (E) -> K,
        valueSelector: (E) -> V,
    ): IStream.One<Map<K, V>> {
        return SingleValueStream(wrapped.associate { keySelector(it) to valueSelector(it) })
    }

    override fun <R> splitMerge(
        predicate: (E) -> Boolean,
        merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
    ): IStream.Many<R> {
        // XXX Sequence.partition reads all entries into two lists, which consumes more memory.
        //     An alternative would be to create two sequences using Sequence.filter, but then the input is iterated
        //     twice. It depends on the use case which one is preferred.
        //     Currently, there is only a single usage of this method in Modelix and that is for bulk queries, which
        //     means the size of the input is limited.
        //     Also, in cases where bulk queries are used the stream will be a Reaktive one and this sequence based
        //     implementation is never called.
        val (a, b) = wrapped.partition(predicate)
        return merger(SequenceAsStream(a.asSequence()), SequenceAsStream(b.asSequence()))
    }

    override fun skip(count: Long): IStream.Many<E> {
        return SequenceAsStream(wrapped.drop(count.toInt()))
    }

    override fun exactlyOne(): IStream.One<E> {
        return SingleValueStream(wrapped.single())
    }

    override fun assertNotEmpty(message: () -> String): IStream.OneOrMany<E> {
        return SequenceAsStreamOneOrMany(wrapped.ifEmpty { throw StreamAssertionError(message()) })
    }

    override fun count(): IStream.One<Int> {
        return SingleValueStream(wrapped.count())
    }

    override fun indexOf(element: E): IStream.One<Int> {
        return SingleValueStream(wrapped.indexOf(element))
    }

    override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
        return convertLater().filterBySingle(condition)
    }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
        return SingleValueStream(wrapped.ifEmpty { sequenceOf(defaultValue()) }.first())
    }

    override fun take(n: Int): IStream.Many<E> {
        return SequenceAsStream(wrapped.take(n))
    }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
        return wrapped.map { SingleValueStream(it) }.take(1).ifEmpty { sequenceOf(EmptyStream<E>()) }.single()
    }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
        return convertLater().switchIfEmpty_(alternative)
    }

    override fun isEmpty(): IStream.One<Boolean> {
        return SingleValueStream(wrapped.map { false }.take(1).ifEmpty { sequenceOf(true) }.single())
    }

    override fun withIndex(): IStream.Many<IndexedValue<E>> {
        return SequenceAsStream(wrapped.withIndex())
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.Many<E> {
        return convertLater().onErrorReturn(valueSupplier)
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.Many<E> {
        return convertLater().doOnBeforeError(consumer)
    }
}

class SequenceAsStreamOneOrMany<E>(wrapped: Sequence<E>) : SequenceAsStream<E>(wrapped), IStream.OneOrMany<E> {
    override fun convertLater(): DeferredStreamBuilder.ConvertibleOneOrMany<E> {
        return DeferredStreamBuilder.ConvertibleOneOrMany { convert(it) }
    }

    override fun convert(converter: IStreamBuilder): IStream.OneOrMany<E> {
        return super.convert(converter).assertNotEmpty { "Empty stream" }
    }

    override fun <R> map(mapper: (E) -> R): IStream.OneOrMany<R> {
        return SequenceAsStreamOneOrMany(wrapped.map(mapper))
    }

    override fun distinct(): IStream.OneOrMany<E> {
        return SequenceAsStreamOneOrMany(wrapped.distinct())
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.OneOrMany<E> {
        return convertLater().onErrorReturn(valueSupplier)
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.OneOrMany<E> {
        return convertLater().doOnBeforeError(consumer)
    }

    override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.OneOrMany<R> {
        return convertLater().flatMapOne(mapper)
    }
}
