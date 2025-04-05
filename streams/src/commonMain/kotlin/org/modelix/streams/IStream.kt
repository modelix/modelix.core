package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import org.modelix.streams.IStream.Many
import org.modelix.streams.IStream.One
import org.modelix.streams.IStream.OneOrMany

interface IStream<out E> {
    fun convert(converter: IStreamBuilder): IStream<E>
    fun asFlow(): Flow<E>
    fun asSequence(): Sequence<E>

    fun toList(): One<List<E>>

    interface Completable : IStream<Any?> {
        override fun convert(converter: IStreamBuilder): IStream.Completable
        fun andThen(other: Completable): Completable
        operator fun <R> plus(other: Many<R>): Many<R>
        operator fun <R> plus(other: ZeroOrOne<R>): ZeroOrOne<R>
        operator fun <R> plus(other: One<R>): One<R>
        operator fun <R> plus(other: OneOrMany<R>): OneOrMany<R>
        fun andThenUnit(): One<Unit> = plus(IStream.of(Unit))
    }

    interface Many<out E> : IStream<E> {
        override fun convert(converter: IStreamBuilder): IStream.Many<E>
        fun filter(predicate: (E) -> Boolean): Many<E>
        fun <R> map(mapper: (E) -> R): Many<R>
        fun <R : Any> mapNotNull(mapper: (E) -> R?): Many<R> = map(mapper).filterNotNull()
        fun <R> flatMap(mapper: (E) -> Many<R>): Many<R>
        fun <R> flatMapIterable(mapper: (E) -> Iterable<R>): Many<R> = flatMap { IStream.many(mapper(it)) }
        fun concat(other: Many<@UnsafeVariance E>): Many<E>
        fun concat(other: OneOrMany<@UnsafeVariance E>): OneOrMany<E>
        fun distinct(): Many<E>
        fun assertEmpty(message: (E) -> String): Completable
        fun assertNotEmpty(message: () -> String): OneOrMany<E>
        fun drainAll(): Completable
        fun <R> fold(initial: R, operation: (R, E) -> R): One<R>
        fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): One<Map<K, V>>
        fun <R> splitMerge(predicate: (E) -> Boolean, merger: (Many<E>, Many<E>) -> Many<R>): Many<R>
        fun skip(count: Long): Many<E>
        fun exactlyOne(): One<E>
        fun count(): One<Int>
        fun filterBySingle(condition: (E) -> One<Boolean>): Many<E>
        fun firstOrDefault(defaultValue: () -> @UnsafeVariance E): One<E>
        fun firstOrDefault(defaultValue: @UnsafeVariance E): One<E> = firstOrDefault { defaultValue }
        fun take(n: Int): Many<E>
        fun firstOrEmpty(): ZeroOrOne<E>
        fun firstOrNull(): One<E?> = firstOrEmpty().orNull()
        fun switchIfEmpty_(alternative: () -> Many<@UnsafeVariance E>): Many<E>
        fun isEmpty(): One<Boolean>
        fun ifEmpty_(alternative: () -> @UnsafeVariance E): OneOrMany<E>
        fun withIndex(): Many<IndexedValue<E>>
        fun onErrorReturn(valueSupplier: (Throwable) -> @UnsafeVariance E): Many<E>
        fun doOnBeforeError(consumer: (Throwable) -> Unit): Many<E>
        fun indexOf(element: @UnsafeVariance E): One<Int>
    }

    interface OneOrMany<out E> : IStream<E>, Many<E> {
        override fun convert(converter: IStreamBuilder): IStream.OneOrMany<E>
        override fun <R> map(mapper: (E) -> R): OneOrMany<R>
        fun <R> flatMapOne(mapper: (E) -> One<R>): OneOrMany<R>
        override fun distinct(): OneOrMany<E>
        override fun onErrorReturn(valueSupplier: (Throwable) -> @UnsafeVariance E): OneOrMany<E>
        override fun doOnBeforeError(consumer: (Throwable) -> Unit): OneOrMany<E>
    }

    interface ZeroOrOne<out E> : IStream<E>, Many<E> {
        override fun convert(converter: IStreamBuilder): IStream.ZeroOrOne<E>
        override fun filter(predicate: (E) -> Boolean): ZeroOrOne<E>
        override fun <R> map(mapper: (E) -> R): ZeroOrOne<R>
        override fun <R : Any> mapNotNull(mapper: (E) -> R?): ZeroOrOne<R> = map(mapper).filterNotNull()
        override fun ifEmpty_(defaultValue: () -> @UnsafeVariance E): One<E>
        fun exceptionIfEmpty(exception: () -> Throwable = { NoSuchElementException() }): One<E>
        fun orNull(): One<E?>
        fun <R> flatMapZeroOrOne(mapper: (E) -> ZeroOrOne<R>): ZeroOrOne<R>
        override fun onErrorReturn(valueSupplier: (Throwable) -> @UnsafeVariance E): ZeroOrOne<E>
        override fun doOnBeforeError(consumer: (Throwable) -> Unit): ZeroOrOne<E>
        override fun assertNotEmpty(message: () -> String): One<E>
    }

    interface One<out E> : IStream<E>, ZeroOrOne<E>, OneOrMany<E> {
        override fun convert(converter: IStreamBuilder): IStream.One<E>
        override fun <R> flatMapOne(mapper: (E) -> One<R>): One<R>
        override fun <R> map(mapper: (E) -> R): One<R>
        fun <T, R> zipWith(other: One<T>, mapper: (E, T) -> R): One<R> = IStream.zip(this, other, mapper)
        fun cached(): One<E>
        override fun onErrorReturn(valueSupplier: (Throwable) -> @UnsafeVariance E): One<E>
        override fun doOnBeforeError(consumer: (Throwable) -> Unit): One<E>
    }

    companion object : IStreamBuilder by DeferredStreamBuilder() {
    }
}

operator fun <R> IStream.Many<R>.plus(other: IStream.Many<R>): IStream.Many<R> {
    return this.concat(other)
}

operator fun <R> IStream.Many<R>.plus(other: Iterable<R>): IStream.Many<R> {
    return this.concat(IStream.many(other))
}

operator fun <R> IStream.Many<R>.plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
    return this.concat(other)
}

fun <T : Any> IStream.One<T?>.notNull(): IStream.ZeroOrOne<T> = filter { it != null } as IStream.ZeroOrOne<T>

fun <T> IStream.Many<IStream.Many<T>>.flatten(): IStream.Many<T> = flatMap { it }
fun <T> IStream.One<IStream.One<T>>.flatten(): IStream.One<T> = flatMapOne { it }
fun <T> IStream.OneOrMany<IStream.One<T>>.flatten(): IStream.OneOrMany<T> = flatMapOne { it }
fun <T> IStream.ZeroOrOne<IStream.ZeroOrOne<T>>.flatten(): IStream.ZeroOrOne<T> = flatMapZeroOrOne { it }
fun <T> IStream.One<IStream.ZeroOrOne<T>>.flatten(): IStream.ZeroOrOne<T> = flatMapZeroOrOne { it }

fun <T : Any> IStream.Many<T?>.filterNotNull(): IStream.Many<T> = filter { it != null } as IStream.Many<T>
fun <T : Any> IStream.ZeroOrOne<T?>.filterNotNull(): IStream.ZeroOrOne<T> = filter { it != null } as IStream.ZeroOrOne<T>

fun <R> IStream.ZeroOrOne<R>.ifEmpty(defaultValue: () -> R): One<R> = ifEmpty_(defaultValue)
fun <R> IStream.Many<R>.switchIfEmpty(alternative: () -> Many<R>): Many<R> = switchIfEmpty_(alternative)
fun <R> IStream.Many<R>.ifEmpty(alternative: R): OneOrMany<R> = ifEmpty_({ alternative })
fun <R> IStream.Many<R>.ifEmpty(alternative: () -> R): OneOrMany<R> = ifEmpty_(alternative)

fun <A, B, R> IStream.ZeroOrOne<Pair<A, B>>.mapFirst(mapper: (A) -> R) = map { mapper(it.first) to it.second }
fun <A, B, R> IStream.One<Pair<A, B>>.mapFirst(mapper: (A) -> R) = map { mapper(it.first) to it.second }
fun <A, B, R> IStream.Many<Pair<A, B>>.mapFirst(mapper: (A) -> R) = map { mapper(it.first) to it.second }

fun <A, B, R> IStream.ZeroOrOne<Pair<A, B>>.mapSecond(mapper: (B) -> R) = map { it.first to mapper(it.second) }
fun <A, B, R> IStream.One<Pair<A, B>>.mapSecond(mapper: (B) -> R) = map { it.first to mapper(it.second) }
fun <A, B, R> IStream.Many<Pair<A, B>>.mapSecond(mapper: (B) -> R) = map { it.first to mapper(it.second) }

fun IStream.Many<*>.isNotEmpty() = isEmpty().map { !it }
fun <T> IStream.Many<T>.contains(element: T): IStream.One<Boolean> = this.filter { it == element }.isNotEmpty()
