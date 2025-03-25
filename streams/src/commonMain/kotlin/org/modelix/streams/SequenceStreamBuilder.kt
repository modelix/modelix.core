package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.modelix.kotlin.utils.DelicateModelixApi

class SequenceStreamBuilder(executor: IStreamExecutorProvider) :
    IStreamBuilder, IStreamExecutorProvider by executor {

    companion object {
        val INSTANCE = SequenceStreamBuilder(SimpleStreamExecutor.asProvider())
    }

    fun <T> convert(stream: IStream<T>) = (stream.convert(this) as WrapperBase<T>).wrapped

    override fun <T> of(element: T): IStream.One<T> = Wrapper(sequenceOf(element))
    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = Wrapper(elements)
    override fun <T> of(vararg elements: T): IStream.Many<T> = Wrapper(elements.asSequence())
    override fun <T> empty(): IStream.ZeroOrOne<T> = Wrapper(emptySequence())

    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> {
        throw UnsupportedOperationException("Use FlowStreamBuilder")
    }

    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> {
        return throw UnsupportedOperationException("Use Reaktive based streams")
    }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> {
        return Wrapper(sequence<IStream.ZeroOrOne<T>> { yield(supplier()) }).flatten()
    }

    override fun zero(): IStream.Zero {
        return Zero(emptySequence())
    }

    override fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> {
        return Wrapper(
            convert(source1)
                .zip(convert(source2))
                .map { mapper(it.first, it.second) },
        )
    }

    override fun <T, R> zip(
        input: Iterable<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): Wrapper<R> {
        val input = input.toList()
        val sequences = input.map { convert(it) }
        return Wrapper(
            when (sequences.size) {
                0 -> emptySequence()
                1 -> sequences.single().map { mapper(listOf(it)) }
                else -> sequences.map { it.map { listOf(it) } }.reduce { a, b -> a.zip(b) { a, b -> a + b } }.map(mapper)
            },
        )
    }

    override fun <T, R> zip(
        input: Iterable<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        return zip(input.map { it.assertNotEmpty { "Empty" } } as Iterable<IStream.Many<T>>, mapper)
    }

    abstract inner class WrapperBase<E>(val wrapped: Sequence<E>) : IStream<E> {
        override fun asFlow(): Flow<E> = wrapped.asFlow()
        override fun toList(): IStream.One<List<E>> = Wrapper(sequence { yield(wrapped.toList()) })
        override fun asSequence(): Sequence<E> = wrapped

        override fun iterateSynchronous(visitor: (E) -> Unit) {
            wrapped.forEach(visitor)
        }

        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            wrapped.forEach { visitor(it) }
        }
    }

    inner class Zero(wrapped: Sequence<Any?>) : WrapperBase<Any?>(wrapped), IStream.Zero, IStreamExecutorProvider by this {
        override fun convert(converter: IStreamBuilder): IStream.Zero {
            require(converter == this@SequenceStreamBuilder)
            return this
        }
        override fun onAfterSubscribe(action: () -> Unit): IStream<Any?> {
            return Zero(
                sequence {
                    action()
                    @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
                    executeSynchronous()
                },
            )
        }

        @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
        override fun executeSynchronous() {
            wrapped.forEach { }
        }

        override fun andThen(other: IStream.Zero): IStream.Zero {
            return Zero(
                sequence {
                    executeSynchronous()
                    @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
                    other.executeSynchronous()
                },
            )
        }

        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
            return plusSequence(convert(other))
        }

        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return plusSequence(convert(other))
        }

        override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
            return plusSequence(convert(other))
        }

        override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
            return plusSequence(convert(other))
        }

        fun <R> plusSequence(other: WrapperBase<R>): Wrapper<R> {
            return Wrapper(
                sequence {
                    @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
                    executeSynchronous()
                    yieldAll(other.asSequence())
                },
            )
        }

        fun <R> plusSequence(other: Sequence<R>): Wrapper<R> {
            return Wrapper(
                sequence {
                    @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
                    executeSynchronous()
                    yieldAll(other)
                },
            )
        }
    }

    inner class Wrapper<E>(wrapped: Sequence<E>) : WrapperBase<E>(wrapped), IStream.One<E>, IStreamExecutorProvider by this {
        override fun convert(converter: IStreamBuilder): IStream.One<E> {
            require(converter == this@SequenceStreamBuilder)
            return this
        }
        fun getAsync(onError: ((Throwable) -> Unit)?, onSuccess: ((E) -> Unit)?) {
            try {
                for (element in wrapped) {
                    onSuccess?.invoke(element)
                }
            } catch (ex: Throwable) {
                onError?.invoke(ex)
            }
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
            return Wrapper(wrapped.flatMap { convert(mapper(it)) })
        }

        override fun <R> map(mapper: (E) -> R): IStream.One<R> {
            return Wrapper(wrapped.map(mapper))
        }

        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
            return Wrapper(wrapped.filter(predicate))
        }

        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
            return Wrapper(wrapped.ifEmpty { sequenceOf(defaultValue()) })
        }

        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
            return Wrapper(wrapped.ifEmpty { throw exception() })
        }

        override fun orNull(): IStream.One<E?> {
            return Wrapper(wrapped.ifEmpty { sequenceOf(null) })
        }

        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return Wrapper(wrapped.flatMap { convert(mapper(it)) })
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return Wrapper(wrapped.flatMap { convert(mapper(it)) })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return Wrapper(wrapped + convert(other))
        }

        override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
            return Wrapper(wrapped + convert(other))
        }

        override fun getSynchronous(): E {
            return wrapped.single()
        }

        override suspend fun getSuspending(): E {
            return wrapped.single()
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return Wrapper(sequenceOf(wrapped.fold(initial, operation)))
        }

        override fun onAfterSubscribe(action: () -> Unit): IStream.One<E> {
            return Wrapper(
                sequence {
                    action()
                    yieldAll(wrapped)
                },
            )
        }

        override fun distinct(): IStream.OneOrMany<E> {
            return Wrapper(wrapped.distinct())
        }

        override fun assertEmpty(message: (E) -> String): IStream.Zero {
            return Zero(wrapped.onEach { throw StreamAssertionError(message(it)) })
        }

        override fun drainAll(): IStream.Zero {
            return Zero(wrapped.filter { false })
        }

        override fun <K, V> toMap(
            keySelector: (E) -> K,
            valueSelector: (E) -> V,
        ): IStream.One<Map<K, V>> {
            return Wrapper(sequenceOf(wrapped.associate { keySelector(it) to valueSelector(it) }))
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
            return merger(Wrapper(a.asSequence()), Wrapper(b.asSequence()))
        }

        override fun cached(): IStream.One<E> {
            val cached by lazy { wrapped.toList() }
            return Wrapper(sequenceOf({ cached }).flatMap { it() })
        }

        override fun skip(count: Long): IStream.Many<E> {
            return Wrapper(wrapped.drop(count.toInt()))
        }

        override fun exactlyOne(): IStream.One<E> {
            return Wrapper(sequence { yield(wrapped.single()) })
        }

        override fun assertNotEmpty(message: () -> String): IStream.One<E> {
            return Wrapper(wrapped.ifEmpty { throw StreamAssertionError(message()) })
        }

        override fun count(): IStream.One<Int> {
            return Wrapper(sequence { yield(wrapped.count()) })
        }

        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
            return Wrapper(wrapped.filter { convert(condition(it)).single() })
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return Wrapper(wrapped.ifEmpty { sequenceOf(defaultValue()) }.take(1))
        }

        override fun take(n: Int): IStream.Many<E> {
            return Wrapper(wrapped.take(n))
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return Wrapper(wrapped.take(1))
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return Wrapper(wrapped.ifEmpty { convert(alternative()) })
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return Wrapper(wrapped.map { false }.ifEmpty { sequenceOf(true) }.take(1))
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return Wrapper(wrapped.withIndex())
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> {
            return Wrapper(
                sequence {
                    try {
                        yieldAll(wrapped)
                    } catch (ex: Throwable) {
                        yield(valueSupplier(ex))
                    }
                },
            )
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> {
            return Wrapper(
                sequence {
                    try {
                        yieldAll(wrapped)
                    } catch (ex: Throwable) {
                        consumer(ex)
                        throw ex
                    }
                },
            )
        }
    }
}

fun <R> IStream.Companion.useSequences(body: () -> R): R {
    return useBuilder(SequenceStreamBuilder.INSTANCE, body)
}
suspend fun <R> IStream.Companion.useSequencesSuspending(body: suspend () -> R): R {
    return useBuilderSuspending(SequenceStreamBuilder.INSTANCE, body)
}
