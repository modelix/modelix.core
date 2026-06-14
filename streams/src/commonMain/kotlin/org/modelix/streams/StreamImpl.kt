package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.kotlin.utils.runBlockingIfJvm
import org.modelix.streams.engine.Done
import org.modelix.streams.engine.Execution
import org.modelix.streams.engine.Step
import org.modelix.streams.engine.asyncStep
import org.modelix.streams.engine.combineConcat
import org.modelix.streams.engine.doOnError
import org.modelix.streams.engine.fanOut
import org.modelix.streams.engine.flatMapStep
import org.modelix.streams.engine.mapValues
import org.modelix.streams.engine.memoStep
import org.modelix.streams.engine.recover
import org.modelix.streams.engine.zipN

class StreamAssertionError(message: String) : IllegalArgumentException(message)

/** Common bridge from a public [IStream] to its internal [Step] representation for the current run. */
internal interface HasStep<out E> {
    fun buildStep(execution: Execution): Step<E>
}

@Suppress("UNCHECKED_CAST")
internal fun <E> IStream<E>.asStep(execution: Execution): Step<E> = (this as HasStep<E>).buildStep(execution)

/**
 * The single backing implementation of every non-[Completable] [IStream] cardinality. The runtime instance always
 * satisfies the strongest interface ([IStreamInternal.One]); static typing at the API boundary restricts which
 * operators are reachable. Operators return new [StreamImpl] instances typed at their most-derived cardinality.
 */
internal class StreamImpl<E>(val build: (Execution) -> Step<E>) : IStreamInternal.One<E>, HasStep<E> {

    override fun buildStep(execution: Execution): Step<E> = build(execution)

    override fun convert(converter: IStreamBuilder): IStream.One<E> = this

    override fun asFlow(): Flow<E> = flow {
        val execution = Execution()
        for (value in execution.driveSuspending(build(execution))) emit(value)
    }

    override fun asSequence(): Sequence<E> {
        val execution = Execution()
        return execution.drive(build(execution)).asSequence()
    }

    override fun toList(): IStream.One<List<E>> = StreamImpl { execution -> build(execution).mapValues { listOf(it) } }

    override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> =
        StreamImpl { execution -> build(execution).mapValues { it.filter(predicate) } }

    override fun <R> map(mapper: (E) -> R): IStream.One<R> =
        StreamImpl { execution -> build(execution).mapValues { it.map(mapper) } }

    override fun <R> flatMapOrdered(mapper: (E) -> IStream.Many<R>): IStream.Many<R> =
        StreamImpl { execution -> build(execution).flatMapStep { values -> fanOut(values) { mapper(it).asStep(execution) } } }

    override fun concat(other: IStream.Many<E>): IStream.Many<E> =
        StreamImpl { execution -> combineConcat(listOf(build(execution), other.asStep(execution))) }

    override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> =
        StreamImpl { execution -> combineConcat(listOf(build(execution), other.asStep(execution))) }

    override fun distinct(): IStream.OneOrMany<E> =
        StreamImpl { execution -> build(execution).mapValues { it.distinct() } }

    override fun assertEmpty(message: (E) -> String): IStream.Completable =
        CompletableImpl { execution ->
            build(execution).mapValues { values ->
                if (values.isNotEmpty()) throw StreamAssertionError(message(values.first()))
                emptyList()
            }
        }

    override fun assertNotEmpty(message: () -> String): IStream.One<E> =
        StreamImpl { execution ->
            build(execution).mapValues { values ->
                if (values.isEmpty()) throw StreamAssertionError("At least one element was expected. " + message())
                values
            }
        }

    override fun drainAll(): IStream.Completable =
        CompletableImpl { execution -> build(execution).mapValues { emptyList<Any?>() } }

    override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> =
        StreamImpl { execution -> build(execution).mapValues { listOf(it.fold(initial, operation)) } }

    override fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): IStream.One<Map<K, V>> =
        StreamImpl { execution -> build(execution).mapValues { values -> listOf(values.associate { keySelector(it) to valueSelector(it) }) } }

    override fun <R> splitMerge(predicate: (E) -> Boolean, merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>): IStream.Many<R> =
        StreamImpl { execution ->
            build(execution).flatMapStep { values ->
                val (matching, notMatching) = values.partition(predicate)
                merger(IStream.many(matching), IStream.many(notMatching)).asStep(execution)
            }
        }

    override fun skip(count: Long): IStream.Many<E> =
        StreamImpl { execution -> build(execution).mapValues { it.drop(count.toInt()) } }

    override fun exactlyOne(): IStream.One<E> =
        StreamImpl { execution -> build(execution).mapValues { listOf(it.single()) } }

    override fun count(): IStream.One<Int> =
        StreamImpl { execution -> build(execution).mapValues { listOf(it.size) } }

    override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> =
        StreamImpl { execution -> build(execution).mapValues { values -> listOf(if (values.isEmpty()) defaultValue() else values.first()) } }

    override fun take(n: Int): IStream.Many<E> =
        StreamImpl { execution -> build(execution).mapValues { it.take(n) } }

    override fun firstOrEmpty(): IStream.ZeroOrOne<E> =
        StreamImpl { execution -> build(execution).mapValues { it.take(1) } }

    override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> =
        StreamImpl { execution -> build(execution).flatMapStep { values -> if (values.isEmpty()) alternative().asStep(execution) else Done(values) } }

    override fun isEmpty(): IStream.One<Boolean> =
        StreamImpl { execution -> build(execution).mapValues { listOf(it.isEmpty()) } }

    override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> =
        StreamImpl { execution -> build(execution).mapValues { values -> if (values.isEmpty()) listOf(defaultValue()) else values } }

    override fun withIndex(): IStream.Many<IndexedValue<E>> =
        StreamImpl { execution -> build(execution).mapValues { it.withIndex().toList() } }

    override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> =
        StreamImpl { execution ->
            try {
                build(execution).recover { listOf(valueSupplier(it)) }
            } catch (ex: Throwable) {
                Done(listOf(valueSupplier(ex)))
            }
        }

    override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> =
        StreamImpl { execution ->
            try {
                build(execution).doOnError(consumer)
            } catch (ex: Throwable) {
                consumer(ex)
                throw ex
            }
        }

    override fun indexOf(element: E): IStream.One<Int> =
        StreamImpl { execution -> build(execution).mapValues { listOf(it.indexOf(element)) } }

    override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> =
        StreamImpl { execution -> build(execution).flatMapStep { values -> mapper(values.single()).asStep(execution) } }

    override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> =
        StreamImpl { execution -> build(execution).flatMapStep { values -> fanOut(values) { mapper(it).asStep(execution) } } }

    override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> =
        StreamImpl { execution -> build(execution).mapValues { values -> if (values.isEmpty()) throw exception() else values } }

    override fun orNull(): IStream.One<E?> =
        StreamImpl { execution -> build(execution).mapValues { values -> if (values.isEmpty()) listOf(null) else listOf(values.single()) } }

    @Suppress("UNCHECKED_CAST")
    override fun cached(): IStream.One<E> {
        val self = this
        val token = Any()
        return StreamImpl { execution -> memoStep(execution, token) { self.build(execution) as Step<Any?> } as Step<E> }
    }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (E) -> Unit) {
        val execution = Execution()
        execution.drive(build(execution)).forEach(visitor)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
        val execution = Execution()
        execution.driveSuspending(build(execution)).forEach { visitor(it) }
    }

    @Suppress("UNCHECKED_CAST")
    @DelicateModelixApi
    override fun getBlocking(): E {
        val execution = Execution()
        val values = execution.drive(build(execution))
        return (if (values.isEmpty()) null else values.first()) as E
    }

    @Suppress("UNCHECKED_CAST")
    @DelicateModelixApi
    override suspend fun getSuspending(): E {
        val execution = Execution()
        val values = execution.driveSuspending(build(execution))
        return (if (values.isEmpty()) null else values.first()) as E
    }
}

/** Backing implementation of [IStream.Completable]. A completion carries no values; its step resolves to an empty list. */
internal class CompletableImpl(val build: (Execution) -> Step<Any?>) : IStreamInternal.Completable, HasStep<Any?> {

    override fun buildStep(execution: Execution): Step<Any?> = build(execution)

    override fun convert(converter: IStreamBuilder): IStream.Completable = this

    override fun asFlow(): Flow<Any?> = flow {
        val execution = Execution()
        for (value in execution.driveSuspending(build(execution))) emit(value)
    }

    override fun asSequence(): Sequence<Any?> {
        val execution = Execution()
        return execution.drive(build(execution)).asSequence()
    }

    override fun toList(): IStream.One<List<Any?>> = StreamImpl { execution -> build(execution).mapValues { listOf(it) } }

    override fun andThen(other: IStream.Completable): IStream.Completable =
        CompletableImpl { execution -> build(execution).flatMapStep { other.asStep(execution) } }

    override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> =
        StreamImpl { execution -> build(execution).flatMapStep { other.asStep(execution) } }

    override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> =
        StreamImpl { execution -> build(execution).flatMapStep { other.asStep(execution) } }

    override fun <R> plus(other: IStream.One<R>): IStream.One<R> =
        StreamImpl { execution -> build(execution).flatMapStep { other.asStep(execution) } }

    override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> =
        StreamImpl { execution -> build(execution).flatMapStep { other.asStep(execution) } }

    @DelicateModelixApi
    override fun iterateBlocking(visitor: (Any?) -> Unit) {
        val execution = Execution()
        execution.drive(build(execution)).forEach(visitor)
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (Any?) -> Unit) {
        val execution = Execution()
        execution.driveSuspending(build(execution)).forEach { visitor(it) }
    }

    @DelicateModelixApi
    override fun executeBlocking() {
        val execution = Execution()
        execution.drive(build(execution))
    }

    @DelicateModelixApi
    override suspend fun executeSuspending() {
        val execution = Execution()
        execution.driveSuspending(build(execution))
    }
}

/** The single [IStreamBuilder] backed by the [Step] engine. Replaces the Sequence/Flow/Reaktive/Deferred builders. */
internal object StreamBuilderImpl : IStreamBuilder {
    override fun zero(): IStream.Completable = CompletableImpl { Done(emptyList()) }

    override fun <T> empty(): IStream.ZeroOrOne<T> = StreamImpl { Done(emptyList()) }

    override fun <T> of(element: T): IStream.One<T> = StreamImpl { Done(listOf(element)) }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> =
        StreamImpl { execution -> supplier().asStep(execution) }

    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = StreamImpl { Done(elements.toList()) }

    @Suppress("UNCHECKED_CAST")
    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> = StreamImpl { execution ->
        asyncStep(
            execution,
            Any(),
            produceBlocking = { runBlockingIfJvm { flow.toList() } },
            produceSuspending = { flow.toList() },
        ) as Step<T>
    }

    override fun <T, R> zip(input: List<IStream.Many<T>>, mapper: (List<T>) -> R): IStream.Many<R> =
        StreamImpl { execution ->
            zipN(input.map { it.asStep(execution) }) { valueLists ->
                val count = if (valueLists.isEmpty()) 0 else valueLists.minOf { it.size }
                (0 until count).map { i -> mapper(valueLists.map { it[i] }) }
            }
        }

    override fun <T, R> zip(input: List<IStream.One<T>>, mapper: (List<T>) -> R): IStream.One<R> =
        StreamImpl { execution ->
            zipN(input.map { it.asStep(execution) }) { valueLists -> listOf(mapper(valueLists.map { it.single() })) }
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> = StreamImpl { execution ->
        asyncStep(
            execution,
            Any(),
            produceBlocking = { runBlockingIfJvm { listOf(coroutineScope { block() }) } },
            produceSuspending = { listOf(coroutineScope { block() }) },
        ) as Step<T>
    }
}
