package org.modelix.streams2

/**
 * A lazily-described stream of values, evaluated in batch rounds by an [IStreamExecutor].
 *
 * This is a clean-room reimplementation of the streaming abstraction with a single execution model:
 * - No incremental emission. A stream is fully materialized when queried; operators like [Many.take] truncate the
 *   materialized result rather than stopping upstream work.
 * - No coroutine/Flow integration. Execution is synchronous and blocking.
 *
 * The one capability that justifies a custom implementation is **automatic bulk-request batching**: independent
 * [fetch][Companion.fetch] requests reachable through applicative composition (the elements of a [Many], the sides of
 * a [zip][Companion.zip] or [One.zipWith]) are coalesced into a single [IBulkExecutor.execute] call, while [flatMap]
 * dependencies fall into later rounds. See [Step] for the mechanism.
 *
 * Cardinality is encoded in the type: [Many] (0+), [ZeroOrOne] (0..1) and [One] (exactly 1).
 */
sealed interface IStream<out E> {

    interface Many<out E> : IStream<E> {
        fun <R> map(transform: (E) -> R): Many<R>
        fun <R : Any> mapNotNull(transform: (E) -> R?): Many<R>
        fun filter(predicate: (E) -> Boolean): Many<E>
        fun <R> flatMap(transform: (E) -> Many<R>): Many<R>
        fun concat(other: Many<@UnsafeVariance E>): Many<E>
        fun distinct(): Many<E>
        fun take(n: Int): Many<E>
        fun skip(n: Int): Many<E>
        fun withIndex(): Many<IndexedValue<E>>

        fun <R> fold(initial: R, operation: (R, E) -> R): One<R>
        fun toList(): One<List<E>>
        fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): One<Map<K, V>>
        fun count(): One<Int>
        fun isEmpty(): One<Boolean>
        fun drainAll(): One<Unit>
        fun assertEmpty(message: (E) -> String): One<Unit>

        fun firstOrEmpty(): ZeroOrOne<E>
        fun firstOrDefault(defaultValue: () -> @UnsafeVariance E): One<E>
        fun exactlyOne(): One<E>
    }

    interface ZeroOrOne<out E> : Many<E> {
        override fun <R> map(transform: (E) -> R): ZeroOrOne<R>
        fun <R> flatMapZeroOrOne(transform: (E) -> ZeroOrOne<R>): ZeroOrOne<R>
        fun orNull(): One<E?>
        fun ifEmpty(defaultValue: () -> @UnsafeVariance E): One<E>
        fun exceptionIfEmpty(exception: () -> Throwable): One<E>
    }

    interface One<out E> : ZeroOrOne<E> {
        override fun <R> map(transform: (E) -> R): One<R>
        fun <R> flatMapOne(transform: (E) -> One<R>): One<R>
        fun <T, R> zipWith(other: One<T>, combine: (E, T) -> R): One<R>
    }

    companion object {
        fun <T> of(value: T): One<T> = stream { Done(listOf(value)) }

        fun <T> empty(): ZeroOrOne<T> = stream { Done(emptyList()) }

        fun <T> many(values: Iterable<T>): Many<T> = stream { Done(values.toList()) }

        fun <T> many(values: Sequence<T>): Many<T> = stream { Done(values.toList()) }

        /** A single value fetched from a batchable [source]. The unit of batching. */
        fun <K, V> fetch(source: IBulkExecutor<K, V>, key: K): One<V> =
            stream { execution -> fetchStep(execution, source, key) }

        /** Applicatively combine independent single-value streams; their fetches share a batch round. */
        fun <T, R> zip(streams: List<One<T>>, combine: (List<T>) -> R): One<R> =
            stream { execution ->
                zipN(streams.map { it.toStep(execution) }) { valueLists -> listOf(combine(valueLists.map { it.single() })) }
            }
    }
}

/** Bridges a public [IStream] back to its internal [Step] representation for the current run. */
internal fun <E> IStream<E>.toStep(execution: Execution): Step<E> = (this as StreamBase<E>).buildStep(execution)

/**
 * The single backing implementation of every [IStream] cardinality. The runtime instance always satisfies the
 * strongest interface ([IStream.One]); static typing at the API boundary restricts which operators are reachable.
 */
internal abstract class StreamBase<out E> : IStream.One<E> {

    internal abstract fun buildStep(execution: Execution): Step<E>

    override fun <R> map(transform: (E) -> R): IStream.One<R> =
        stream { execution -> buildStep(execution).mapValues { values -> values.map(transform) } }

    override fun <R : Any> mapNotNull(transform: (E) -> R?): IStream.Many<R> =
        stream { execution -> buildStep(execution).mapValues { values -> values.mapNotNull(transform) } }

    override fun filter(predicate: (E) -> Boolean): IStream.Many<E> =
        stream { execution -> buildStep(execution).mapValues { values -> values.filter(predicate) } }

    override fun <R> flatMap(transform: (E) -> IStream.Many<R>): IStream.Many<R> =
        stream { execution ->
            buildStep(execution).flatMapStep { values -> combineConcat(values.map { transform(it).toStep(execution) }) }
        }

    override fun concat(other: IStream.Many<@UnsafeVariance E>): IStream.Many<E> =
        stream { execution -> combineConcat(listOf(buildStep(execution), other.toStep(execution))) }

    override fun distinct(): IStream.Many<E> =
        stream { execution -> buildStep(execution).mapValues { it.distinct() } }

    override fun take(n: Int): IStream.Many<E> =
        stream { execution -> buildStep(execution).mapValues { it.take(n) } }

    override fun skip(n: Int): IStream.Many<E> =
        stream { execution -> buildStep(execution).mapValues { it.drop(n) } }

    override fun withIndex(): IStream.Many<IndexedValue<E>> =
        stream { execution -> buildStep(execution).mapValues { it.withIndex().toList() } }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> =
        stream { execution -> buildStep(execution).mapValues { listOf(it.fold(initial, operation)) } }

    override fun toList(): IStream.One<List<E>> =
        stream { execution -> buildStep(execution).mapValues { listOf(it) } }

    override fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): IStream.One<Map<K, V>> =
        stream { execution -> buildStep(execution).mapValues { values -> listOf(values.associate { keySelector(it) to valueSelector(it) }) } }

    override fun count(): IStream.One<Int> =
        stream { execution -> buildStep(execution).mapValues { listOf(it.size) } }

    override fun isEmpty(): IStream.One<Boolean> =
        stream { execution -> buildStep(execution).mapValues { listOf(it.isEmpty()) } }

    override fun drainAll(): IStream.One<Unit> =
        stream { execution -> buildStep(execution).mapValues { listOf(Unit) } }

    override fun assertEmpty(message: (E) -> String): IStream.One<Unit> =
        stream { execution ->
            buildStep(execution).mapValues { values ->
                if (values.isNotEmpty()) throw IllegalStateException(message(values.first()))
                listOf(Unit)
            }
        }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> =
        stream { execution -> buildStep(execution).mapValues { it.take(1) } }

    override fun firstOrDefault(defaultValue: () -> @UnsafeVariance E): IStream.One<E> =
        stream { execution -> buildStep(execution).mapValues { values -> listOf(values.firstOrNull() ?: defaultValue()) } }

    override fun exactlyOne(): IStream.One<E> =
        stream { execution -> buildStep(execution).mapValues { listOf(it.single()) } }

    override fun <R> flatMapZeroOrOne(transform: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> =
        stream { execution ->
            buildStep(execution).flatMapStep { values -> combineConcat(values.map { transform(it).toStep(execution) }) }
        }

    override fun orNull(): IStream.One<E?> =
        stream { execution -> buildStep(execution).mapValues { values -> if (values.isEmpty()) listOf(null) else listOf(values.single()) } }

    override fun ifEmpty(defaultValue: () -> @UnsafeVariance E): IStream.One<E> =
        stream { execution -> buildStep(execution).mapValues { values -> if (values.isEmpty()) listOf(defaultValue()) else values } }

    override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> =
        stream { execution -> buildStep(execution).mapValues { values -> if (values.isEmpty()) throw exception() else values } }

    override fun <R> flatMapOne(transform: (E) -> IStream.One<R>): IStream.One<R> =
        stream { execution -> buildStep(execution).flatMapStep { values -> transform(values.single()).toStep(execution) } }

    override fun <T, R> zipWith(other: IStream.One<T>, combine: (E, T) -> R): IStream.One<R> =
        stream { execution ->
            zip2(buildStep(execution), other.toStep(execution)) { a, b -> listOf(combine(a.single(), b.single())) }
        }
}

private class StreamNode<E>(private val builder: (Execution) -> Step<E>) : StreamBase<E>() {
    override fun buildStep(execution: Execution): Step<E> = builder(execution)
}

internal fun <E> stream(builder: (Execution) -> Step<E>): StreamBase<E> = StreamNode(builder)
