package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.flow.zip

class FlowStreamBuilder : IStreamBuilder {
    override fun <T> of(element: T): IStream.One<T> = Wrapper(flowOf(element))
    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = Wrapper(elements.asFlow())
    override fun <T> of(vararg elements: T): IStream.Many<T> = Wrapper(elements.asFlow())
    override fun <T> empty(): IStream.ZeroOrOne<T> = Wrapper(emptyFlow())

    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> {
        return Wrapper(
            flow {
                coroutineScope {
                    emit(block())
                }
            },
        )
    }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> {
        return Wrapper(flow<IStream.ZeroOrOne<T>> { emit(supplier()) }).flatten()
    }

    override fun zero(): IStream.Zero {
        return Zero(emptyFlow())
    }

    override fun <T, R> zip(
        input: Iterable<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        TODO("Not yet implemented")
    }

    override fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> {
        return Wrapper(
            (source1 as Wrapper<T1>).wrapped.zip((source2 as Wrapper<T2>).wrapped) { a, b ->
                mapper(a, b)
            },
        )
    }

    override fun <T, R> zip(
        input: Iterable<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): IStream.Many<R> {
        TODO("Not yet implemented")
    }

    abstract class WrapperBase<E>(val wrapped: Flow<E>) : IStream<E> {
        override fun asFlow(): Flow<E> = wrapped
        override fun toList(): IStream.One<List<E>> = Wrapper(flow { emit(wrapped.toList()) })
        override fun asSequence(): Sequence<E> = throw UnsupportedOperationException()

        override fun iterateSynchronous(visitor: (E) -> Unit) = throw UnsupportedOperationException()
        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            wrapped.collect(visitor)
        }
    }

    class Zero(wrapped: Flow<Any?>) : WrapperBase<Any?>(wrapped), IStream.Zero {
        override fun onAfterSubscribe(action: () -> Unit): IStream<Any?> {
            return Zero(
                flow {
                    action()
                    wrapped.collect()
                },
            )
        }
        override fun executeSynchronous() {
            throw UnsupportedOperationException()
        }

        override fun andThen(other: IStream.Zero): IStream.Zero {
            return Zero(
                flow {
                    wrapped.collect { }
                    other.asFlow().collect { }
                },
            )
        }

        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
            return plusSequence(other as WrapperBase<R>)
        }

        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return plusSequence(other as WrapperBase<R>)
        }

        override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
            return plusSequence(other as WrapperBase<R>)
        }

        override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
            return plusSequence(other as WrapperBase<R>)
        }

        fun <R> plusSequence(other: WrapperBase<R>): Wrapper<R> {
            return Wrapper(
                flow {
                    executeSynchronous()
                    emitAll(other.asFlow())
                },
            )
        }
    }

    class Wrapper<E>(wrapped: Flow<E>) : WrapperBase<E>(wrapped), IStream.One<E> {
        override fun getAsync(onError: ((Throwable) -> Unit)?, onSuccess: ((E) -> Unit)?) {
            throw UnsupportedOperationException()
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
            return Wrapper(wrapped.flatMapConcat { (mapper(it) as Wrapper<R>).wrapped })
        }

        override fun <R> map(mapper: (E) -> R): IStream.One<R> {
            return Wrapper(wrapped.map { mapper(it) })
        }

        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
            return Wrapper(wrapped.filter { predicate(it) })
        }

        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
            return Wrapper(wrapped.onEmpty { emit(defaultValue()) })
        }

        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
            return Wrapper(wrapped.onEmpty { throw exception() })
        }

        override fun orNull(): IStream.One<E?> {
            return Wrapper(wrapped.onEmpty<E?> { emit(null) })
        }

        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return Wrapper(wrapped.flatMapConcat { (mapper(it) as Wrapper<R>).wrapped })
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return Wrapper(wrapped.flatMapConcat { (mapper(it) as Wrapper<R>).wrapped })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return Wrapper(wrapped + (other as Wrapper<E>).wrapped)
        }

        override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
            return Wrapper(wrapped + (other as Wrapper<E>).wrapped)
        }

        override fun getSynchronous(): E {
            return throw UnsupportedOperationException()
        }

        override suspend fun getSuspending(): E {
            return wrapped.single()
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return Wrapper(flow { wrapped.fold(initial) { acc, value -> operation(acc, value) } })
        }

        override fun onAfterSubscribe(action: () -> Unit): IStream.One<E> {
            return Wrapper(
                flow {
                    action()
                    emitAll(wrapped)
                },
            )
        }

        override fun distinct(): IStream.OneOrMany<E> {
            return Wrapper(wrapped.distinctUntilChanged())
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
            return Wrapper(
                flow {
                    val map = LinkedHashMap<K, V>()
                    wrapped.collect {
                        map[keySelector(it)] = valueSelector(it)
                    }
                    emit(map)
                },
            )
        }

        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> {
            throw UnsupportedOperationException()
        }

        override fun cached(): IStream.One<E> {
            throw UnsupportedOperationException()
        }

        override fun skip(count: Long): IStream.Many<E> {
            return Wrapper(wrapped.drop(count.toInt()))
        }

        override fun exactlyOne(): IStream.One<E> {
            return Wrapper(flow { emit(wrapped.single()) })
        }

        override fun assertNotEmpty(message: () -> String): IStream.OneOrMany<E> {
            return Wrapper(wrapped.onEmpty { throw NoSuchElementException(message()) })
        }

        override fun count(): IStream.One<Int> {
            return Wrapper(flow { emit(wrapped.count()) })
        }

        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
            return Wrapper(wrapped.filter { (condition(it) as Wrapper<Boolean>).wrapped.single() })
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return Wrapper(wrapped.onEmpty { emit(defaultValue()) }.take(1))
        }

        override fun take(n: Int): IStream.Many<E> {
            return Wrapper(wrapped.take(n))
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return Wrapper(wrapped.take(1))
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return Wrapper(wrapped.onEmpty { emitAll((alternative() as Wrapper<E>).wrapped) })
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return Wrapper(wrapped.map { false }.onEmpty { emit(true) }.take(1))
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return Wrapper(wrapped.withIndex())
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> {
            return Wrapper(wrapped.catch { emit(valueSupplier(it)) })
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> {
            return Wrapper(
                wrapped.catch {
                    consumer(it)
                    throw it
                },
            )
        }
    }
}

private operator fun <R> Flow<R>.plus(other: Flow<R>) = onCompletion { if (it == null) emitAll(other) }
