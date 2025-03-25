package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single
import org.modelix.kotlin.utils.DelicateModelixApi

class DeferredStreamBuilder : IStreamBuilder {
    override fun zero(): IStream.Zero {
        return ConvertibleZero { it.zero() }
    }

    override fun <T> empty(): IStream.ZeroOrOne<T> {
        return Convertible { it.empty() }
    }

    override fun <T> of(element: T): IStream.One<T> {
        return Convertible { it.of(element) }
    }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> {
        return Convertible { it.deferZeroOrOne(supplier) }
    }

    override fun <T> many(elements: Sequence<T>): IStream.Many<T> {
        return Convertible { it.many(elements) }
    }

    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> {
        return Convertible { it.fromFlow(flow) }
    }

    override fun <T, R> zip(
        input: Iterable<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): IStream.Many<R> {
        return Convertible { it.zip(input, mapper) }
    }

    override fun <T, R> zip(
        input: Iterable<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        return Convertible { it.zip(input, mapper) }
    }

    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> {
        return Convertible { it.singleFromCoroutine(block) }
    }

    override fun getStreamExecutor(): IStreamExecutor {
        TODO("Not yet implemented")
    }

    class Convertible<E>(conversion: (IStreamBuilder) -> IStream<E>) : ConvertibleBase<E>(conversion), IStream.One<E> {
        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> = fail()
        override fun <R> map(mapper: (E) -> R): IStream.One<R> = fail()

        @DelicateModelixApi
        override fun getSynchronous(): E = SequenceStreamBuilder(SimpleStreamExecutor().asProvider()).convert(this).single()

        @DelicateModelixApi
        override suspend fun getSuspending(): E = FlowStreamBuilder(SimpleStreamExecutor().asProvider()).convert(this).single()
        override fun cached(): IStream.One<E> = fail()
        override fun getAsync(onError: ((Throwable) -> Unit)?, onSuccess: ((E) -> Unit)?) = fail()
        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> = fail()
        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> = fail()
        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> = fail()
        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> = fail()
        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> = fail()
        override fun orNull(): IStream.One<E?> = fail()
        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> = fail()
        override fun assertNotEmpty(message: () -> String): IStream.One<E> = fail()
        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> = fail()
        override fun concat(other: IStream.Many<E>): IStream.Many<E> = fail()
        override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> = fail()
        override fun distinct(): IStream.OneOrMany<E> = fail()
        override fun assertEmpty(message: (E) -> String): IStream.Zero = fail()
        override fun drainAll(): IStream.Zero = fail()
        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> = fail()
        override fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): IStream.One<Map<K, V>> = fail()
        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> = fail()
        override fun skip(count: Long): IStream.Many<E> = fail()
        override fun exactlyOne(): IStream.One<E> = fail()
        override fun count(): IStream.One<Int> = fail()
        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> = fail()
        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> = fail()
        override fun take(n: Int): IStream.Many<E> = fail()
        override fun firstOrEmpty(): IStream.ZeroOrOne<E> = fail()
        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> = fail()
        override fun isEmpty(): IStream.One<Boolean> = fail()
        override fun withIndex(): IStream.Many<IndexedValue<E>> = fail()
    }

    class ConvertibleZero(conversion: (IStreamBuilder) -> IStream<*>) : ConvertibleBase<Any?>(conversion), IStream.Zero {
        @DelicateModelixApi
        override fun executeSynchronous() = SequenceStreamBuilder(SimpleStreamExecutor().asProvider()).convert(this).forEach { }
        override fun andThen(other: IStream.Zero): IStream.Zero = fail()
        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> = fail()
        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> = fail()
        override fun <R> plus(other: IStream.One<R>): IStream.One<R> = fail()
        override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> = fail()
    }

    abstract class ConvertibleBase<E>(val conversion: (IStreamBuilder) -> IStream<E>) : IStream<E> {
        override fun convert(converter: IStreamBuilder): IStream<E> {
            return conversion(converter)
        }

        override fun asFlow(): Flow<E> {
            val converted = conversion(FlowStreamBuilder(SimpleStreamExecutor().asProvider()))
            return (converted as FlowStreamBuilder.WrapperBase).wrapped
        }

        override fun asSequence(): Sequence<E> {
            val converted = conversion(SequenceStreamBuilder(SimpleStreamExecutor().asProvider()))
            return (converted as SequenceStreamBuilder.WrapperBase).wrapped
        }

        protected fun fail(): Nothing = throw UnsupportedOperationException("Call convert(IStreamConverter) first")
        override fun onAfterSubscribe(action: () -> Unit): IStream.One<E> = fail()
        override fun toList(): IStream.One<List<E>> = fail()

        @DelicateModelixApi
        override fun iterateSynchronous(visitor: (E) -> Unit) {
            SequenceStreamBuilder(SimpleStreamExecutor().asProvider()).convert(this).forEach(visitor)
        }

        @DelicateModelixApi
        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            FlowStreamBuilder(SimpleStreamExecutor().asProvider()).convert(this).collect(visitor)
        }
        override fun getStreamExecutor(): IStreamExecutor = fail()
    }
}
