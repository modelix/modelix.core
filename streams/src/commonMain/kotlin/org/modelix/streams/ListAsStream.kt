package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.modelix.kotlin.utils.DelicateModelixApi

open class ListAsStream<E>(val list: List<E>) : IStream.Many<E> {
    protected open fun convertLater() = DeferredStreamBuilder.ConvertibleMany { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.Many<E> {
        return converter.many(list)
    }

    override fun filter(predicate: (E) -> Boolean): IStream.Many<E> {
        return ListAsStream(list.filter(predicate))
    }

    override fun <R> map(mapper: (E) -> R): IStream.Many<R> {
        return ListAsStream(list.map(mapper))
    }

    override fun <R : Any> mapNotNull(mapper: (E) -> R?): IStream.Many<R> {
        return ListAsStream(list.mapNotNull(mapper))
    }

    override fun firstOrNull(): IStream.One<E?> {
        return SingleValueStream(list.firstOrNull())
    }

    override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
        return convertLater().flatMap(mapper)
    }

    override fun <R> flatMapIterable(mapper: (E) -> Iterable<R>): IStream.Many<R> {
        return SequenceAsStream(list.asSequence().flatMap { mapper(it) })
    }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> {
        return when (other) {
            is SingleValueStream<E> -> ListAsStream(list + other.value)
            is SequenceAsStream<E> -> SequenceAsStream(list.asSequence() + other.wrapped)
            is EmptyStream<E> -> this
            is ListAsStream<E> -> ListAsStream(list + other.list)
            else -> convertLater().concat(other)
        }
    }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
        return when (other) {
            is SingleValueStream<E> -> ListAsStreamOneOrMany(list + other.value)
            is SequenceAsStreamOneOrMany<E> -> SequenceAsStreamOneOrMany(list.asSequence() + other.wrapped)
            is ListAsStreamOneOrMany<E> -> ListAsStreamOneOrMany(list + other.list)
            else -> convertLater().concat(other)
        }
    }

    override fun distinct(): IStream.Many<E> {
        return ListAsStream(list.distinct())
    }

    override fun assertEmpty(message: (E) -> String): IStream.Completable {
        if (list.isNotEmpty()) throw StreamAssertionError("Not empty: $list")
        return EmptyCompletableStream()
    }

    override fun assertNotEmpty(message: () -> String): IStream.OneOrMany<E> {
        if (list.isEmpty()) throw NoSuchElementException("Empty stream")
        return ListAsStreamOneOrMany(list)
    }

    override fun drainAll(): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
        return SingleValueStream(list.fold(initial, operation))
    }

    override fun <K, V> toMap(
        keySelector: (E) -> K,
        valueSelector: (E) -> V,
    ): IStream.One<Map<K, V>> {
        return SingleValueStream(list.associate { keySelector(it) to valueSelector(it) })
    }

    override fun <R> splitMerge(
        predicate: (E) -> Boolean,
        merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
    ): IStream.Many<R> {
        val (a, b) = list.partition(predicate)
        return merger(ListAsStream(a), ListAsStream(b))
    }

    override fun skip(count: Long): IStream.Many<E> {
        return ListAsStream(list.drop(count.toInt()))
    }

    override fun exactlyOne(): IStream.One<E> {
        return SingleValueStream(list.single())
    }

    override fun count(): IStream.One<Int> {
        return SingleValueStream(list.size)
    }

    override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
        return convertLater().filterBySingle(condition)
    }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
        return SingleValueStream(if (list.isEmpty()) defaultValue() else list[0])
    }

    override fun firstOrDefault(defaultValue: E): IStream.One<E> {
        return SingleValueStream(if (list.isEmpty()) defaultValue else list[0])
    }

    override fun take(n: Int): IStream.Many<E> {
        return ListAsStream(list.take(n))
    }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
        return if (list.isEmpty()) EmptyStream() else SingleValueStream(list[0])
    }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
        return if (list.isEmpty()) alternative() else this
    }

    override fun isEmpty(): IStream.One<Boolean> {
        return SingleValueStream(list.isEmpty())
    }

    override fun ifEmpty_(alternative: () -> E): IStream.OneOrMany<E> {
        return if (list.isEmpty()) SingleValueStream(alternative()) else ListAsStreamOneOrMany(list)
    }

    override fun withIndex(): IStream.Many<IndexedValue<E>> {
        return SequenceAsStream(list.withIndex().asSequence())
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.Many<E> {
        return this
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.Many<E> {
        return this
    }

    override fun asFlow(): Flow<E> {
        return list.asFlow()
    }

    override fun asSequence(): Sequence<E> {
        return list.asSequence()
    }

    override fun toList(): IStream.One<List<E>> {
        return SingleValueStream(list)
    }

    @DelicateModelixApi
    override fun iterateSynchronous(visitor: (E) -> Unit) {
        list.forEach(visitor)
    }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (E) -> Unit) {
        list.forEach(visitor)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
        list.forEach { visitor(it) }
    }
}

class ListAsStreamOneOrMany<E>(list: List<E>) : ListAsStream<E>(list), IStream.OneOrMany<E> {
    protected override fun convertLater() = DeferredStreamBuilder.ConvertibleOneOrMany { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.OneOrMany<E> {
        return converter.many(list).assertNotEmpty { "Empty stream" }
    }

    override fun <R> map(mapper: (E) -> R): IStream.OneOrMany<R> {
        return ListAsStreamOneOrMany(list.map(mapper))
    }

    override fun distinct(): IStream.OneOrMany<E> {
        return ListAsStreamOneOrMany(list.distinct())
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.OneOrMany<E> {
        return this
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.OneOrMany<E> {
        return this
    }

    override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.OneOrMany<R> {
        return convertLater().flatMapOne(mapper)
    }
}
