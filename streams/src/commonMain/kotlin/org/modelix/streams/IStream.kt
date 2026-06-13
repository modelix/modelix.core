package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import org.modelix.streams.IStream.Many
import org.modelix.streams.IStream.One
import org.modelix.streams.IStream.OneOrMany
import org.modelix.streams.IStream.ZeroOrOne
import kotlin.jvm.JvmName

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

        @Deprecated("Use flatMapOrdered or flatMapUnordered")
        fun <R> flatMap(mapper: (E) -> Many<R>): Many<R> = flatMapOrdered(mapper)

        /**
         * Output elements are only emitted after all output elements of the previous input are emitted.
         * Can have a lower performance than [flatMapUnordered].
         */
        fun <R> flatMapOrdered(mapper: (E) -> Many<R>): Many<R>

        /**
         * Output elements are emitted as soon as possible.
         * Can have a higher performance than [flatMapOrdered].
         */
        fun <R> flatMapUnordered(mapper: (E) -> Many<R>): Many<R> = flatMapOrdered(mapper)

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
        fun filterBySingle(condition: (E) -> One<Boolean>): Many<E> {
            return flatMap { element ->
                condition(element).map { included -> element to included }
            }.filter { it.second }.map { it.first }
        }
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

    companion object : IStreamBuilder by StreamBuilderImpl {
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

fun <T> IStream.Many<T>.forEach(action: (T) -> Unit): IStream.Completable = map(action).drainAll()
fun <T> IStream.Many<T>.onEach(action: (T) -> Unit): IStream.Many<T> = map {
    action(it)
    it
}

// --- Boolean reductions ---

/** Emits `true` if at least one element satisfies [predicate], `false` otherwise (and for an empty stream). */
fun <T> IStream.Many<T>.any(predicate: (T) -> Boolean): IStream.One<Boolean> = filter(predicate).isNotEmpty()

/** Emits `true` if every element satisfies [predicate]. An empty stream emits `true`. */
fun <T> IStream.Many<T>.all(predicate: (T) -> Boolean): IStream.One<Boolean> = filter { !predicate(it) }.isEmpty()

/** Emits `true` if no element satisfies [predicate]. An empty stream emits `true`. */
fun <T> IStream.Many<T>.none(predicate: (T) -> Boolean): IStream.One<Boolean> = filter(predicate).isEmpty()

/** Emits `true` if the stream contains no elements. */
fun IStream.Many<*>.none(): IStream.One<Boolean> = isEmpty()

// --- Filtering ---

/** Keeps the elements that do *not* satisfy [predicate]. */
fun <T> IStream.Many<T>.filterNot(predicate: (T) -> Boolean): IStream.Many<T> = filter { !predicate(it) }

/** Keeps only elements that are instances of [R]. */
inline fun <reified R : Any> IStream.Many<*>.filterIsInstance(): IStream.Many<R> = mapNotNull { it as? R }

/** Keeps the elements for which [predicate] (receiving the element's index) returns `true`. */
fun <T> IStream.Many<T>.filterIndexed(predicate: (Int, T) -> Boolean): IStream.Many<T> =
    withIndex().filter { predicate(it.index, it.value) }.map { it.value }

/** Emits at most [count] elements counted from the start of the stream. Alias for [IStream.Many.skip]. */
fun <T> IStream.Many<T>.drop(count: Int): IStream.Many<T> = skip(count.toLong())

// --- Element access ---

/** Emits the first element, or fails with [NoSuchElementException] if the stream is empty. */
fun <T> IStream.Many<T>.first(): IStream.One<T> =
    firstOrEmpty().exceptionIfEmpty { NoSuchElementException("Stream is empty") }

/** Emits the first element satisfying [predicate], or fails with [NoSuchElementException] if there is none. */
fun <T> IStream.Many<T>.first(predicate: (T) -> Boolean): IStream.One<T> = filter(predicate).first()

/** Emits the first element satisfying [predicate], or `null` if there is none. */
fun <T> IStream.Many<T>.firstOrNull(predicate: (T) -> Boolean): IStream.One<T?> = filter(predicate).firstOrNull()

/** Emits the last element, or fails with [NoSuchElementException] if the stream is empty. */
fun <T> IStream.Many<T>.last(): IStream.One<T> = toList().map { it.last() }

/** Emits the last element, or `null` if the stream is empty. */
fun <T> IStream.Many<T>.lastOrNull(): IStream.One<T?> = toList().map { it.lastOrNull() }

// --- Indexed / element-wise mapping ---

/** Maps each element together with its index. */
fun <T, R> IStream.Many<T>.mapIndexed(mapper: (Int, T) -> R): IStream.Many<R> =
    withIndex().map { mapper(it.index, it.value) }

// --- Collection conversions ---

/** Collects all elements into a [Set]. */
fun <T> IStream.Many<T>.toSet(): IStream.One<Set<T>> = toList().map { it.toSet() }

/** Groups the elements by the key returned by [keySelector]. */
fun <T, K> IStream.Many<T>.groupBy(keySelector: (T) -> K): IStream.One<Map<K, List<T>>> =
    toList().map { it.groupBy(keySelector) }

/** Groups the elements by [keySelector], mapping each grouped element through [valueSelector]. */
fun <T, K, V> IStream.Many<T>.groupBy(keySelector: (T) -> K, valueSelector: (T) -> V): IStream.One<Map<K, List<V>>> =
    toList().map { it.groupBy(keySelector, valueSelector) }

/** Associates each element with the key produced by [keySelector]. Later duplicates win. */
fun <T, K> IStream.Many<T>.associateBy(keySelector: (T) -> K): IStream.One<Map<K, T>> = toMap(keySelector) { it }

/** Associates each element (used as the key) with the value produced by [valueSelector]. */
fun <T, V> IStream.Many<T>.associateWith(valueSelector: (T) -> V): IStream.One<Map<T, V>> = toMap({ it }, valueSelector)

/** Collects a stream of pairs into a [Map]. Later duplicate keys win. */
fun <K, V> IStream.Many<Pair<K, V>>.toMap(): IStream.One<Map<K, V>> = toMap({ it.first }, { it.second })

// --- Sorting ---

fun <T : Comparable<T>> IStream.Many<T>.sorted(): IStream.Many<T> = toList().flatMapIterable { it.sorted() }
fun <T : Comparable<T>> IStream.Many<T>.sortedDescending(): IStream.Many<T> = toList().flatMapIterable { it.sortedDescending() }
fun <T, R : Comparable<R>> IStream.Many<T>.sortedBy(selector: (T) -> R): IStream.Many<T> =
    toList().flatMapIterable { it.sortedBy(selector) }
fun <T, R : Comparable<R>> IStream.Many<T>.sortedByDescending(selector: (T) -> R): IStream.Many<T> =
    toList().flatMapIterable { it.sortedByDescending(selector) }
fun <T> IStream.Many<T>.sortedWith(comparator: Comparator<in T>): IStream.Many<T> =
    toList().flatMapIterable { it.sortedWith(comparator) }

// --- distinct / dedup ---

/** Removes elements that share a key (as returned by [selector]) with an earlier element. */
fun <T, K> IStream.Many<T>.distinctBy(selector: (T) -> K): IStream.Many<T> =
    toList().flatMapIterable { it.distinctBy(selector) }

// --- Numeric and general reductions ---

/** Reduces the stream with [operation], starting from the first element. Fails on an empty stream. */
fun <T> IStream.Many<T>.reduce(operation: (acc: T, T) -> T): IStream.One<T> = toList().map { it.reduce(operation) }

/** Sums the [Int] values produced by [selector]. */
fun <T> IStream.Many<T>.sumOf(selector: (T) -> Int): IStream.One<Int> = fold(0) { acc, e -> acc + selector(e) }

@JvmName("sumInt")
fun IStream.Many<Int>.sum(): IStream.One<Int> = fold(0) { acc, e -> acc + e }

@JvmName("sumLong")
fun IStream.Many<Long>.sum(): IStream.One<Long> = fold(0L) { acc, e -> acc + e }

@JvmName("sumDouble")
fun IStream.Many<Double>.sum(): IStream.One<Double> = fold(0.0) { acc, e -> acc + e }

/** Emits the element yielding the largest value by [selector], or `null` if the stream is empty. */
fun <T, R : Comparable<R>> IStream.Many<T>.maxByOrNull(selector: (T) -> R): IStream.One<T?> =
    toList().map { it.maxByOrNull(selector) }

/** Emits the element yielding the smallest value by [selector], or `null` if the stream is empty. */
fun <T, R : Comparable<R>> IStream.Many<T>.minByOrNull(selector: (T) -> R): IStream.One<T?> =
    toList().map { it.minByOrNull(selector) }

/** Emits the largest element according to [comparator], or `null` if the stream is empty. */
fun <T> IStream.Many<T>.maxWithOrNull(comparator: Comparator<in T>): IStream.One<T?> =
    toList().map { it.maxWithOrNull(comparator) }

/** Emits the smallest element according to [comparator], or `null` if the stream is empty. */
fun <T> IStream.Many<T>.minWithOrNull(comparator: Comparator<in T>): IStream.One<T?> =
    toList().map { it.minWithOrNull(comparator) }

// --- Prepending / appending single elements ---

/** Emits [value] before all elements of this stream. */
fun <T> IStream.Many<T>.startWith(value: T): IStream.Many<T> = IStream.of(value).concat(this)

/** Emits [value] after all elements of this stream. */
fun <T> IStream.Many<T>.endWith(value: T): IStream.OneOrMany<T> = concat(IStream.of(value))

// --- String joining ---

/** Joins all elements into a single [String]. */
fun <T> IStream.Many<T>.joinToString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    transform: ((T) -> CharSequence)? = null,
): IStream.One<String> = toList().map { it.joinToString(separator, prefix, postfix, transform = transform) }
