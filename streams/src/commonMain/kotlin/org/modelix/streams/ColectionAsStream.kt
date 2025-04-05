package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.modelix.kotlin.utils.DelicateModelixApi

open class ColectionAsStream<E>(val collection: Collection<E>) : IStream.Many<E>, IStreamInternal<E> {
    protected open fun convertLater() = DeferredStreamBuilder.ConvertibleMany { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.Many<E> {
        return converter.many(collection)
    }

    override fun filter(predicate: (E) -> Boolean): IStream.Many<E> {
        return ColectionAsStream(collection.filter(predicate))
    }

    override fun <R> map(mapper: (E) -> R): IStream.Many<R> {
        return ColectionAsStream(collection.map(mapper))
    }

    override fun <R : Any> mapNotNull(mapper: (E) -> R?): IStream.Many<R> {
        return ColectionAsStream(collection.mapNotNull(mapper))
    }

    override fun firstOrNull(): IStream.One<E?> {
        return SingleValueStream(collection.firstOrNull())
    }

    override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
        return convertLater().flatMap(mapper)
    }

    override fun <R> flatMapIterable(mapper: (E) -> Iterable<R>): IStream.Many<R> {
        return SequenceAsStream(collection.asSequence().flatMap { mapper(it) })
    }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> {
        return when (other) {
            is SingleValueStream<E> -> ColectionAsStream(collection + other.value)
            is SequenceAsStream<E> -> SequenceAsStream(collection.asSequence() + other.wrapped)
            is EmptyStream<E> -> this
            is ColectionAsStream<E> -> ColectionAsStream(collection + other.collection)
            else -> convertLater().concat(other)
        }
    }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
        return when (other) {
            is SingleValueStream<E> -> ColectionAsStreamOneOrMany(collection + other.value)
            is SequenceAsStreamOneOrMany<E> -> SequenceAsStreamOneOrMany(collection.asSequence() + other.wrapped)
            is ColectionAsStreamOneOrMany<E> -> ColectionAsStreamOneOrMany(collection + other.collection)
            else -> convertLater().concat(other)
        }
    }

    override fun distinct(): IStream.Many<E> {
        return ColectionAsStream(collection.distinct())
    }

    override fun assertEmpty(message: (E) -> String): IStream.Completable {
        if (collection.isNotEmpty()) throw StreamAssertionError("Not empty: $collection")
        return EmptyCompletableStream()
    }

    override fun assertNotEmpty(message: () -> String): IStream.OneOrMany<E> {
        if (collection.isEmpty()) throw NoSuchElementException("Empty stream")
        return ColectionAsStreamOneOrMany(collection)
    }

    override fun drainAll(): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
        return SingleValueStream(collection.fold(initial, operation))
    }

    override fun <K, V> toMap(
        keySelector: (E) -> K,
        valueSelector: (E) -> V,
    ): IStream.One<Map<K, V>> {
        return SingleValueStream(collection.associate { keySelector(it) to valueSelector(it) })
    }

    override fun <R> splitMerge(
        predicate: (E) -> Boolean,
        merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
    ): IStream.Many<R> {
        val (a, b) = collection.partition(predicate)
        return merger(ColectionAsStream(a), ColectionAsStream(b))
    }

    override fun skip(count: Long): IStream.Many<E> {
        return ColectionAsStream(collection.drop(count.toInt()))
    }

    override fun exactlyOne(): IStream.One<E> {
        return SingleValueStream(collection.single())
    }

    override fun count(): IStream.One<Int> {
        return SingleValueStream(collection.size)
    }

    override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
        return convertLater().filterBySingle(condition)
    }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
        return SingleValueStream(if (collection.isEmpty()) defaultValue() else collection.first())
    }

    override fun firstOrDefault(defaultValue: E): IStream.One<E> {
        return SingleValueStream(if (collection.isEmpty()) defaultValue else collection.first())
    }

    override fun take(n: Int): IStream.Many<E> {
        return ColectionAsStream(collection.take(n))
    }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
        return if (collection.isEmpty()) EmptyStream() else SingleValueStream(collection.first())
    }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
        return if (collection.isEmpty()) alternative() else this
    }

    override fun isEmpty(): IStream.One<Boolean> {
        return SingleValueStream(collection.isEmpty())
    }

    override fun ifEmpty_(alternative: () -> E): IStream.OneOrMany<E> {
        return if (collection.isEmpty()) SingleValueStream(alternative()) else ColectionAsStreamOneOrMany(collection)
    }

    override fun withIndex(): IStream.Many<IndexedValue<E>> {
        return SequenceAsStream(collection.withIndex().asSequence())
    }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.Many<E> {
        return this
    }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.Many<E> {
        return this
    }

    override fun asFlow(): Flow<E> {
        return collection.asFlow()
    }

    override fun asSequence(): Sequence<E> {
        return collection.asSequence()
    }

    override fun toList(): IStream.One<List<E>> {
        return SingleValueStream((collection as? List<E>) ?: collection.toList())
    }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (E) -> Unit) {
        collection.forEach(visitor)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
        collection.forEach { visitor(it) }
    }
}

class ColectionAsStreamOneOrMany<E>(list: Collection<E>) : ColectionAsStream<E>(list), IStream.OneOrMany<E> {
    protected override fun convertLater() = DeferredStreamBuilder.ConvertibleOneOrMany { convert(it) }

    override fun convert(converter: IStreamBuilder): IStream.OneOrMany<E> {
        return converter.many(collection).assertNotEmpty { "Empty stream" }
    }

    override fun <R> map(mapper: (E) -> R): IStream.OneOrMany<R> {
        return ColectionAsStreamOneOrMany(collection.map(mapper))
    }

    override fun distinct(): IStream.OneOrMany<E> {
        return ColectionAsStreamOneOrMany(collection.distinct())
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
