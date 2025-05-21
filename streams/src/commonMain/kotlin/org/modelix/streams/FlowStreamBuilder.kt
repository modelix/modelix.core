package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
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
import org.modelix.kotlin.utils.DelicateModelixApi

class FlowStreamBuilder() : IStreamBuilder {

    companion object {
        val INSTANCE = FlowStreamBuilder()
    }

    fun <T> convert(stream: IStream<T>) = (stream.convert(this) as WrapperBase<T>).wrapped

    override fun <T> of(element: T): IStream.One<T> = Wrapper(flowOf(element))
    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = Wrapper(elements.asFlow())
    override fun <T> of(vararg elements: T): IStream.Many<T> = Wrapper(elements.asFlow())
    override fun <T> empty(): IStream.ZeroOrOne<T> = Wrapper(emptyFlow())
    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> = Wrapper(flow)

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

    override fun zero(): IStream.Completable {
        return Completable(emptyFlow())
    }

    override fun <T, R> zip(
        input: List<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        return Wrapper(
            flow {
                emit(mapper(input.map { convert(it).single() }))
            },
        )
    }

    override fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> {
        return Wrapper(
            convert(source1).zip(convert(source2)) { a, b ->
                mapper(a, b)
            },
        )
    }

    override fun <T, R> zip(
        input: List<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): IStream.Many<R> {
        TODO("Not yet implemented")
    }

    abstract inner class WrapperBase<E>(val wrapped: Flow<E>) : IStreamInternal<E> {
        override fun asFlow(): Flow<E> = wrapped
        override fun toList(): IStream.One<List<E>> = Wrapper(flow { emit(wrapped.toList()) })
        override fun asSequence(): Sequence<E> = throw UnsupportedOperationException()

        override fun iterateBlocking(visitor: (E) -> Unit) {
            throw UnsupportedOperationException("Use suspendable queries")
        }
        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            wrapped.collect(visitor)
        }
    }

    inner class Completable(wrapped: Flow<Any?>) : WrapperBase<Any?>(wrapped), IStreamInternal.Completable {
        override fun convert(converter: IStreamBuilder): IStream.Completable {
            require(converter == this@FlowStreamBuilder)
            return this
        }

        override fun executeBlocking() {
            throw UnsupportedOperationException("Use suspendable queries")
        }

        @DelicateModelixApi
        override suspend fun executeSuspending() {
            wrapped.collect { }
        }

        override fun andThen(other: IStream.Completable): IStream.Completable {
            return Completable(
                flow {
                    wrapped.collect { }
                    other.asFlow().collect { }
                },
            )
        }

        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
            return plusFlow(convert(other))
        }

        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return plusFlow(convert(other))
        }

        override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
            return plusFlow(convert(other))
        }

        override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
            return plusFlow(convert(other))
        }

        fun <R> plusFlow(other: Flow<R>): Wrapper<R> {
            return Wrapper(
                flow {
                    @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
                    executeSuspending()
                    emitAll(other)
                },
            )
        }
    }

    inner class Wrapper<E>(wrapped: Flow<E>) : WrapperBase<E>(wrapped), IStreamInternal.One<E> {
        override fun convert(converter: IStreamBuilder): IStream.One<E> {
            require(converter == this@FlowStreamBuilder)
            return this
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
            return Wrapper(wrapped.flatMapConcat { convert(mapper(it)) })
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
            return Wrapper(wrapped.flatMapConcat { convert(mapper(it)) })
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return Wrapper(wrapped.flatMapConcat { convert(mapper(it)) })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return Wrapper(wrapped + convert(other))
        }

        override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
            return Wrapper(wrapped + convert(other))
        }

        override fun getBlocking(): E {
            throw UnsupportedOperationException("Use suspendable queries")
        }

        override suspend fun getSuspending(): E {
            return wrapped.single()
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return Wrapper(
                flow {
                    emit(wrapped.fold(initial) { acc, value -> operation(acc, value) })
                },
            )
        }

        override fun distinct(): IStream.OneOrMany<E> {
            return Wrapper(wrapped.distinctUntilChanged())
        }

        override fun assertEmpty(message: (E) -> String): IStream.Completable {
            return Completable(wrapped.onEach { throw StreamAssertionError(message(it)) })
        }

        override fun drainAll(): IStream.Completable {
            return Completable(wrapped.filter { false })
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

        override fun assertNotEmpty(message: () -> String): IStream.One<E> {
            return Wrapper(wrapped.onEmpty { throw NoSuchElementException(message()) })
        }

        override fun count(): IStream.One<Int> {
            return Wrapper(flow { emit(wrapped.count()) })
        }

        override fun indexOf(element: E): IStream.One<Int> {
            return Wrapper(flow { emit(wrapped.withIndex().firstOrNull { it == element }?.index ?: -1) })
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
            return Wrapper(wrapped.onEmpty { emitAll(convert(alternative())) })
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
