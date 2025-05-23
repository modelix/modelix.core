package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single
import org.modelix.kotlin.utils.DelicateModelixApi

class DeferredStreamBuilder : IStreamBuilder {
    override fun zero(): IStream.Completable {
        return EmptyCompletableStream()
    }

    override fun <T> empty(): IStream.ZeroOrOne<T> {
        return EmptyStream()
    }

    override fun <T> of(element: T): IStream.One<T> {
        return SingleValueStream(element)
    }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> {
        return ConvertibleZeroOrOne { it.deferZeroOrOne(supplier) }
    }

    override fun <T> many(elements: Collection<T>): IStream.Many<T> {
        return CollectionAsStream(elements)
    }

    override fun <T> many(elements: Sequence<T>): IStream.Many<T> {
        return SequenceAsStream(elements)
    }

    override fun <T> many(elements: Iterable<T>): IStream.Many<T> {
        return SequenceAsStream(elements.asSequence())
    }

    override fun <T> many(elements: Array<T>): IStream.Many<T> {
        return CollectionAsStream(elements.asList())
    }

    override fun many(elements: LongArray): IStream.Many<Long> {
        return CollectionAsStream(elements.asList())
    }

    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> {
        return ConvertibleMany { it.fromFlow(flow) }
    }

    override fun <T, R> zip(
        input: List<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): IStream.Many<R> {
        return ConvertibleMany { it.zip(input, mapper) }
    }

    override fun <T, R> zip(
        input: List<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        if (input.all { it is SingleValueStream }) {
            return SingleValueStream(mapper(input.map { (it as SingleValueStream).value }))
        }
        return ConvertibleOne { c ->
            c.zip(input.map { it.convert(c) }, mapper)
        }
    }

    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> {
        return ConvertibleOne { it.singleFromCoroutine(block) }
    }

    class ConvertibleOne<E>(conversion: (IStreamBuilder) -> IStream.One<E>) : ConvertibleZeroOrOne<E>(conversion), IStreamInternal.One<E> {
        override fun convert(converter: IStreamBuilder): IStream.One<E> {
            return super.convert(converter) as IStream.One<E>
        }

        @DelicateModelixApi
        override fun getBlocking(): E = SequenceStreamBuilder.INSTANCE.convert(this).single()

        @DelicateModelixApi
        override suspend fun getSuspending(): E = FlowStreamBuilder.INSTANCE.convert(this).single()

        override fun <R> map(mapper: (E) -> R): IStream.One<R> {
            return ConvertibleOne { convert(it).map(mapper) }
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> {
            return ConvertibleOne { convert(it).onErrorReturn(valueSupplier) }
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> {
            return ConvertibleOne { convert(it).doOnBeforeError(consumer) }
        }

        override fun distinct(): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).distinct() }
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
            return ConvertibleOne { c -> convert(c).flatMapOne { mapper(it).convert(c) } }
        }

        override fun cached(): IStream.One<E> {
            return ConvertibleOne { convert(it).cached() }
        }
    }

    open class ConvertibleZeroOrOne<E>(conversion: (IStreamBuilder) -> IStream.ZeroOrOne<E>) : ConvertibleMany<E>(conversion), IStream.ZeroOrOne<E> {
        override fun convert(converter: IStreamBuilder): IStream.ZeroOrOne<E> {
            return super.convert(converter) as IStream.ZeroOrOne<E>
        }

        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
            return ConvertibleZeroOrOne { convert(it).filter(predicate) }
        }

        override fun <R> map(mapper: (E) -> R): IStream.ZeroOrOne<R> {
            return ConvertibleZeroOrOne { convert(it).map(mapper) }
        }

        override fun assertNotEmpty(message: () -> String): IStream.One<E> {
            return ConvertibleOne { convert(it).assertNotEmpty(message) }
        }

        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
            return ConvertibleOne { convert(it).ifEmpty_(defaultValue) }
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.ZeroOrOne<E> {
            return ConvertibleZeroOrOne { convert(it).onErrorReturn(valueSupplier) }
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.ZeroOrOne<E> {
            return ConvertibleZeroOrOne { convert(it).doOnBeforeError(consumer) }
        }

        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
            return ConvertibleOne { convert(it).exceptionIfEmpty(exception) }
        }

        override fun orNull(): IStream.One<E?> {
            return ConvertibleOne { convert(it).orNull() }
        }

        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return ConvertibleZeroOrOne { converter ->
                convert(converter).flatMapZeroOrOne { mapper(it).convert(converter) }
            }
        }
    }

    open class ConvertibleMany<E>(conversion: (IStreamBuilder) -> IStream.Many<E>) : ConvertibleBase<E>(conversion), IStream.Many<E> {
        override fun convert(converter: IStreamBuilder): IStream.Many<E> {
            return super.convert(converter) as IStream.Many<E>
        }

        override fun filter(predicate: (E) -> Boolean): IStream.Many<E> {
            return ConvertibleMany { convert(it).filter(predicate) }
        }

        override fun <R> map(mapper: (E) -> R): IStream.Many<R> {
            return ConvertibleMany { convert(it).map(mapper) }
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return ConvertibleMany { converter ->
                convert(converter).flatMap { mapper(it).convert(converter) }
            }
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return ConvertibleMany { convert(it).concat(other.convert(it)) }
        }

        override fun concat(other: IStream.OneOrMany<E>): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).concat(other.convert(it)) }
        }

        override fun distinct(): IStream.Many<E> {
            return ConvertibleMany { convert(it).distinct() }
        }

        override fun assertEmpty(message: (E) -> String): IStream.Completable {
            return ConvertibleCompletable { convert(it).assertEmpty(message) }
        }

        override fun assertNotEmpty(message: () -> String): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).assertNotEmpty(message) }
        }

        override fun drainAll(): IStream.Completable {
            return ConvertibleCompletable { convert(it).drainAll() }
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return ConvertibleOne { convert(it).fold(initial, operation) }
        }

        override fun <K, V> toMap(
            keySelector: (E) -> K,
            valueSelector: (E) -> V,
        ): IStream.One<Map<K, V>> {
            return ConvertibleOne { convert(it).toMap(keySelector, valueSelector) }
        }

        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> {
            return ConvertibleMany { c ->
                convert(c).splitMerge(predicate) { a, b -> merger(a.convert(c), b.convert(c)) }
            }
        }

        override fun skip(count: Long): IStream.Many<E> {
            return ConvertibleMany { convert(it).skip(count) }
        }

        override fun exactlyOne(): IStream.One<E> {
            return ConvertibleOne { convert(it).exactlyOne() }
        }

        override fun count(): IStream.One<Int> {
            return ConvertibleOne { convert(it).count() }
        }

        override fun indexOf(element: E): IStream.One<Int> {
            return ConvertibleOne { convert(it).indexOf(element) }
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return ConvertibleOne { convert(it).firstOrDefault(defaultValue) }
        }

        override fun take(n: Int): IStream.Many<E> {
            return ConvertibleMany { convert(it).take(n) }
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return ConvertibleZeroOrOne { convert(it).firstOrEmpty() }
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return ConvertibleMany { c -> convert(c).switchIfEmpty_ { alternative().convert(c) } }
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return ConvertibleOne { convert(it).isEmpty() }
        }

        override fun ifEmpty_(alternative: () -> E): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).ifEmpty_(alternative) }
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return ConvertibleMany { convert(it).withIndex() }
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.Many<E> {
            return ConvertibleMany { convert(it).onErrorReturn(valueSupplier) }
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.Many<E> {
            return ConvertibleMany { convert(it).doOnBeforeError(consumer) }
        }
    }

    class ConvertibleOneOrMany<E>(conversion: (IStreamBuilder) -> IStream.OneOrMany<E>) : ConvertibleMany<E>(conversion), IStream.OneOrMany<E> {
        override fun convert(converter: IStreamBuilder): IStream.OneOrMany<E> {
            return super.convert(converter) as IStream.OneOrMany<E>
        }

        override fun <R> map(mapper: (E) -> R): IStream.OneOrMany<R> {
            return ConvertibleOneOrMany { convert(it).map(mapper) }
        }

        override fun distinct(): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).distinct() }
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).onErrorReturn(valueSupplier) }
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.OneOrMany<E> {
            return ConvertibleOneOrMany { convert(it).doOnBeforeError(consumer) }
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.OneOrMany<R> {
            return ConvertibleOneOrMany { c -> convert(c).flatMapOne { mapper(it).convert(c) } }
        }
    }

    class ConvertibleCompletable(conversion: (IStreamBuilder) -> IStream.Completable) : ConvertibleBase<Any?>(conversion), IStreamInternal.Completable {
        override fun convert(converter: IStreamBuilder): IStream.Completable {
            return super.convert(converter) as IStream.Completable
        }

        @DelicateModelixApi
        override fun executeBlocking() = SequenceStreamBuilder.INSTANCE.convert(this).forEach { }

        @DelicateModelixApi
        override fun iterateBlocking(visitor: (Any?) -> Unit) {
            executeBlocking()
        }

        @DelicateModelixApi
        override suspend fun iterateSuspending(visitor: suspend (Any?) -> Unit) {
            executeSuspending()
        }

        @DelicateModelixApi
        override suspend fun executeSuspending() {
            FlowStreamBuilder.INSTANCE.convert(this).collect {}
        }

        override fun andThen(other: IStream.Completable): IStream.Completable {
            return ConvertibleCompletable { convert(it).andThen(other.convert(it)) }
        }
        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
            return ConvertibleMany { convert(it).plus(other.convert(it)) }
        }
        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return ConvertibleZeroOrOne { convert(it).plus(other.convert(it)) }
        }
        override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
            return ConvertibleOne { convert(it).plus(other.convert(it)) }
        }
        override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
            return ConvertibleOneOrMany { convert(it).plus(other.convert(it)) }
        }
    }

    abstract class ConvertibleBase<E>(private val conversion: (IStreamBuilder) -> IStream<E>) : IStreamInternal<E> {
        private var converter: IStreamBuilder? = null

        /**
         * Without caching of the converted stream [IStream.One.cached] doesn't work.
         */
        private val converted: Pair<IStream<E>, IStreamBuilder> by lazy { converter.let { conversion(it!!) to it } }

        override fun convert(converter: IStreamBuilder): IStream<E> {
            this.converter = converter
            check(converted.second == converter) { "Converter changed ${converted.second} -> $converter" }
            return converted.first
        }

        override fun asFlow(): Flow<E> {
            val converted = convert(FlowStreamBuilder.INSTANCE)
            return (converted as FlowStreamBuilder.WrapperBase).wrapped
        }

        override fun asSequence(): Sequence<E> {
            val converted = convert(SequenceStreamBuilder.INSTANCE)
            return (converted as SequenceStreamBuilder.WrapperBase).wrapped
        }

        override fun toList(): IStream.One<List<E>> = ConvertibleOne { convert(it).toList() }

        @DelicateModelixApi
        override fun iterateBlocking(visitor: (E) -> Unit) {
            SequenceStreamBuilder.INSTANCE.convert(this).forEach(visitor)
        }

        @DelicateModelixApi
        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            FlowStreamBuilder.INSTANCE.convert(this).collect(visitor)
        }
    }
}
