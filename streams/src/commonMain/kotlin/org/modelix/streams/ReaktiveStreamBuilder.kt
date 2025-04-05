package org.modelix.streams

import com.badoo.reaktive.base.Source
import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.completable.andThen
import com.badoo.reaktive.completable.asMaybe
import com.badoo.reaktive.completable.asObservable
import com.badoo.reaktive.completable.completableOfEmpty
import com.badoo.reaktive.coroutinesinterop.asObservable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asCompletable
import com.badoo.reaktive.maybe.asObservable
import com.badoo.reaktive.maybe.asSingle
import com.badoo.reaktive.maybe.asSingleOrError
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.maybe.doOnBeforeError
import com.badoo.reaktive.maybe.filter
import com.badoo.reaktive.maybe.flatMap
import com.badoo.reaktive.maybe.flatMapObservable
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.maybe.maybeDefer
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.onErrorReturn
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asCompletable
import com.badoo.reaktive.observable.autoConnect
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.doOnBeforeError
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.firstOrComplete
import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.onErrorReturn
import com.badoo.reaktive.observable.publish
import com.badoo.reaktive.observable.skip
import com.badoo.reaktive.observable.switchIfEmpty
import com.badoo.reaktive.observable.take
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.observable.toMap
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asCompletable
import com.badoo.reaktive.single.asMaybe
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.doOnBeforeError
import com.badoo.reaktive.single.filter
import com.badoo.reaktive.single.flatMap
import com.badoo.reaktive.single.flatMapMaybe
import com.badoo.reaktive.single.flatMapObservable
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.onErrorReturn
import com.badoo.reaktive.single.singleOf
import com.badoo.reaktive.single.subscribe
import com.badoo.reaktive.single.zipWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream.OneOrMany

class ReaktiveStreamBuilder() : IStreamBuilder {

    fun convert(stream: IStream.Completable) = (stream.convert(this) as Wrapper<*>).wrappedAsCompletable()
    fun <T> convert(stream: IStream.One<T>) = (stream.convert(this) as Wrapper<T>).wrappedAsSingle()
    fun <T> convert(stream: IStream.ZeroOrOne<T>) = (stream.convert(this) as Wrapper<T>).wrappedAsMaybe()
    fun <T> convert(stream: IStream.Many<T>) = (stream.convert(this) as Wrapper<T>).wrappedAsObservable()

    override fun <T> of(element: T): IStream.One<T> = WrapperSingle(singleOf(element))
    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = WrapperMany(elements.asObservable())
    override fun <T> of(vararg elements: T): IStream.Many<T> = WrapperMany(elements.asObservable())
    override fun <T> empty(): IStream.ZeroOrOne<T> = WrapperMaybe(maybeOfEmpty())
    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> = WrapperMany(flow.asObservable())

    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> {
        return WrapperSingle(com.badoo.reaktive.coroutinesinterop.singleFromCoroutine(block))
    }

    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> {
        return WrapperMaybe(
            maybeDefer {
                supplier().toReaktive()
            },
        )
    }

    override fun zero(): IStream.Completable {
        return WrapperCompletable(completableOfEmpty())
    }

    override fun <T, R> zip(
        input: List<IStream.One<T>>,
        mapper: (List<T>) -> R,
    ): IStream.One<R> {
        return WrapperSingle(
            com.badoo.reaktive.single.zip(*input.map { it.toReaktive() }.toTypedArray()) {
                mapper(it)
            },
        )
    }

    override fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> {
        return WrapperSingle(source1.toReaktive().zipWith(source2.toReaktive(), mapper))
    }

    override fun <T, R> zip(
        input: List<IStream.Many<T>>,
        mapper: (List<T>) -> R,
    ): IStream.Many<R> {
        return WrapperMany(
            com.badoo.reaktive.observable.zip(*input.map { it.toReaktive() }.toTypedArray()) {
                mapper(it)
            },
        )
    }

    abstract inner class Wrapper<E> {
        abstract fun wrappedAsSingle(): Single<E>
        abstract fun wrappedAsMaybe(): Maybe<E>
        abstract fun wrappedAsObservable(): Observable<E>
        abstract fun wrappedAsCompletable(): Completable
    }

    abstract inner class ReaktiveWrapper<E> : Wrapper<E>(), IStream.Many<E>, IStreamInternal<E> {
        abstract val wrapped: Source<*>
        override fun iterateBlocking(visitor: (E) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }
        override suspend fun iterateSuspending(visitor: suspend (E) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterateSuspending")
        }

        override fun skip(count: Long): IStream.Many<E> {
            require(count >= 0L)
            return WrapperMany(wrappedAsObservable().skip(count))
        }

        override fun count(): IStream.One<Int> {
            return WrapperSingle(wrappedAsObservable().count())
        }

        override fun take(n: Int): IStream.Many<E> {
            return WrapperMany(wrappedAsObservable().take(n))
        }

        override fun toList(): IStream.One<List<E>> {
            return WrapperSingle(wrappedAsObservable().toList())
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return WrapperSingle(wrappedAsObservable().isEmpty())
        }

        override fun exactlyOne(): IStream.One<E> {
            return WrapperSingle(wrappedAsSingle())
        }

        override fun concat(other: OneOrMany<E>): OneOrMany<E> {
            return WrapperOneOrMany(wrappedAsObservable().concatWith(other.toReaktive()))
        }
    }

    inner class WrapperCompletable(val wrapped: Completable) :
        Wrapper<Unit>(), IStreamInternal.Completable {
        override fun convert(converter: IStreamBuilder): IStream.Completable {
            require(converter == this@ReaktiveStreamBuilder)
            return this
        }
        override fun wrappedAsSingle(): Single<Unit> = throw NoSuchElementException()
        override fun wrappedAsMaybe(): Maybe<Unit> = wrapped.asMaybe()
        override fun wrappedAsObservable(): Observable<Unit> = wrapped.asObservable()
        override fun wrappedAsCompletable(): Completable = wrapped

        override fun executeBlocking() {
            throw UnsupportedOperationException("Use IStreamExecutor.query")
        }

        override suspend fun iterateSuspending(visitor: suspend (Any?) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterateSuspending")
        }

        override fun andThen(other: IStream.Completable): IStream.Completable {
            return WrapperCompletable(wrapped.andThen(other.toReaktive()))
        }

        override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
            return WrapperMany(wrapped.andThen(other.toReaktive()))
        }

        override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return WrapperMaybe(wrapped.andThen(other.toReaktive()))
        }

        override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
            return WrapperSingle(wrapped.andThen(other.toReaktive()))
        }

        override fun <R> plus(other: OneOrMany<R>): OneOrMany<R> {
            return WrapperOneOrMany(wrapped.andThen(other.toReaktive()))
        }

        override fun asFlow(): Flow<Any?> {
            throw UnsupportedOperationException("Use IStreamExecutor.iterateSuspending")
        }

        override fun asSequence(): Sequence<Any?> {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun toList(): IStream.One<List<Any?>> {
            return WrapperSingle(wrapped.asObservable().toList())
        }

        override fun iterateBlocking(visitor: (Any?) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        @DelicateModelixApi
        override suspend fun executeSuspending() {
            throw UnsupportedOperationException("Use IStreamExecutor.iterateSuspending")
        }
    }

    open inner class WrapperMany<E>(override val wrapped: Observable<E>) :
        ReaktiveWrapper<E>(), IStream.Many<E> {
        override fun convert(converter: IStreamBuilder): IStream.Many<E> {
            require(converter == this@ReaktiveStreamBuilder)
            return this
        }
        override fun wrappedAsObservable(): Observable<E> = wrapped
        override fun wrappedAsSingle(): Single<E> = wrapped.exactlyOne()
        override fun wrappedAsMaybe(): Maybe<E> = wrapped.firstOrComplete()
        override fun wrappedAsCompletable(): Completable = wrapped.asCompletable()

        override fun filter(predicate: (E) -> Boolean): IStream.Many<E> {
            return WrapperMany(wrapped.filter(predicate))
        }

        override fun ifEmpty_(alternative: () -> E): OneOrMany<E> {
            return WrapperOneOrMany(wrapped.switchIfEmpty { observableOf(alternative()) })
        }

        override fun <R> map(mapper: (E) -> R): IStream.Many<R> {
            return WrapperMany(wrapped.map(mapper))
        }

        override fun asFlow(): Flow<E> {
            throw UnsupportedOperationException()
            // return wrapped.asFlow()
        }

        override fun asSequence(): Sequence<E> {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun iterateBlocking(visitor: (E) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return WrapperMany(wrapped.flatMap { mapper(it).toReaktive() })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return WrapperMany(wrapped.concatWith(other.toReaktive()))
        }

        override fun distinct(): IStream.Many<E> {
            return WrapperMany(wrapped.distinct())
        }

        override fun assertEmpty(message: (E) -> String): IStream.Completable {
            return WrapperCompletable(wrapped.assertEmpty(message))
        }

        override fun assertNotEmpty(message: () -> String): OneOrMany<E> {
            return WrapperOneOrMany<E>(wrapped.assertNotEmpty(message))
        }

        override fun drainAll(): IStream.Completable {
            return WrapperCompletable(wrapped.asCompletable())
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return WrapperSingle(wrapped.fold(initial, operation))
        }

        override fun <K, V> toMap(keySelector: (E) -> K, valueSelector: (E) -> V): IStream.One<Map<K, V>> {
            return WrapperSingle(wrapped.toMap(keySelector, valueSelector))
        }

        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> {
            val sharedInput = wrappedAsObservable().publish().autoConnect(2)
            val a = sharedInput.filter { predicate(it) == true }
            val b = sharedInput.filter { predicate(it) == false }
            return merger(WrapperMany(a), WrapperMany(b))
        }

        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
            return WrapperMany(wrapped.filterBySingle { condition(it).toReaktive() })
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return WrapperSingle(wrapped.firstOrDefault(defaultValue))
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return WrapperMaybe(wrappedAsMaybe())
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return WrapperMany(wrapped.switchIfEmpty { alternative().toReaktive() })
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return WrapperSingle(wrapped.isEmpty())
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return WrapperMany(wrapped.withIndex())
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.Many<E> {
            return WrapperMany(wrapped.onErrorReturn(valueSupplier))
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.Many<E> {
            return WrapperMany(wrapped.doOnBeforeError(consumer))
        }

        override fun indexOf(element: E): IStream.One<Int> {
            return WrapperSingle(wrapped.withIndex().firstOrNull().map { it?.index ?: -1 })
        }
    }

    inner class WrapperOneOrMany<E>(wrapped: Observable<E>) :
        WrapperMany<E>(wrapped), OneOrMany<E> {
        override fun convert(converter: IStreamBuilder): OneOrMany<E> {
            require(converter == this@ReaktiveStreamBuilder)
            return this
        }

        override fun wrappedAsObservable(): Observable<E> = wrapped
        override fun wrappedAsSingle(): Single<E> = wrapped.exactlyOne()
        override fun wrappedAsMaybe(): Maybe<E> = wrapped.firstOrComplete()

        override fun <R> map(mapper: (E) -> R): OneOrMany<R> {
            return WrapperOneOrMany(wrapped.map(mapper))
        }

        override fun distinct(): OneOrMany<E> {
            return WrapperOneOrMany(wrapped.distinct())
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): OneOrMany<R> {
            return WrapperOneOrMany(wrapped.flatMapSingle { mapper(it).toReaktive() })
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): OneOrMany<E> {
            return WrapperOneOrMany(wrapped.onErrorReturn(valueSupplier))
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): OneOrMany<E> {
            return WrapperOneOrMany(wrapped.doOnBeforeError(consumer))
        }
    }

    inner class WrapperSingle<E>(override val wrapped: Single<E>) : ReaktiveWrapper<E>(), IStreamInternal.One<E> {
        override fun convert(converter: IStreamBuilder): IStream.One<E> {
            require(converter == this@ReaktiveStreamBuilder)
            return this
        }
        override fun wrappedAsObservable(): Observable<E> = wrapped.asObservable()
        override fun wrappedAsSingle(): Single<E> = wrapped
        override fun wrappedAsMaybe(): Maybe<E> = wrapped.asMaybe()
        override fun wrappedAsCompletable(): Completable = wrapped.asCompletable()

        fun getAsync(onError: ((Throwable) -> Unit)?, onSuccess: ((E) -> Unit)?) {
            wrappedAsSingle().subscribe(onError = onError, onSuccess = onSuccess)
        }

        override fun cached(): IStream.One<E> {
            return WrapperSingle(wrappedAsSingle().cached())
        }

        override fun <R> flatMapOne(mapper: (E) -> IStream.One<R>): IStream.One<R> {
            return WrapperSingle(wrapped.flatMap { mapper(it).toReaktive() })
        }

        override fun <R> map(mapper: (E) -> R): IStream.One<R> {
            return WrapperSingle(wrapped.map(mapper))
        }

        override fun getBlocking(): E {
            throw UnsupportedOperationException("Use IStreamExecutor.query")
        }

        override suspend fun getSuspending(): E {
            throw UnsupportedOperationException("Use IStreamExecutor.querySuspending")
        }

        override fun asFlow(): Flow<E> {
            throw UnsupportedOperationException()
            // return wrapped.asObservable().asFlow()
        }

        override fun asSequence(): Sequence<E> {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun toList(): IStream.One<List<E>> {
            return WrapperSingle(wrapped.map { listOf(it) })
        }

        override fun iterateBlocking(visitor: (E) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
            return WrapperMaybe(wrapped.filter(predicate))
        }

        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
            return this // cannot be empty
        }

        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
            return this // cannot be empty
        }

        override fun orNull(): IStream.One<E?> {
            return this // cannot be empty
        }

        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return WrapperMaybe(wrapped.flatMapMaybe { mapper(it).toReaktive() })
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return WrapperMany(wrapped.flatMapObservable { mapper(it).toReaktive() })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return WrapperMany(wrapped.asObservable().concatWith(other.toReaktive()))
        }

        override fun distinct(): OneOrMany<E> {
            return this // single element is always distinct
        }

        override fun assertEmpty(message: (E) -> String): IStream.Completable {
            throw StreamAssertionError("Single will never be empty: $wrapped")
        }

        override fun assertNotEmpty(message: () -> String): IStream.One<E> {
            return this // cannot be empty
        }

        override fun drainAll(): IStream.Completable {
            return WrapperCompletable(wrapped.asCompletable())
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return WrapperSingle(wrapped.map { operation(initial, it) })
        }

        override fun <K, V> toMap(
            keySelector: (E) -> K,
            valueSelector: (E) -> V,
        ): IStream.One<Map<K, V>> {
            return WrapperSingle(wrapped.map { mapOf(keySelector(it) to valueSelector(it)) })
        }

        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> {
            TODO("Not yet implemented")
        }

        override fun exactlyOne(): IStream.One<E> {
            return this
        }

        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
            return WrapperMany(wrapped.asObservable().filterBySingle { condition(it).toReaktive() })
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return this // there is always a first element
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return this // there is always a first element
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return this // never empty
        }

        override fun isEmpty(): IStream.One<Boolean> {
            return WrapperSingle(wrapped.asObservable().isEmpty())
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return WrapperSingle(wrapped.map { IndexedValue(0, it) })
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.One<E> {
            return WrapperSingle(wrapped.onErrorReturn(valueSupplier))
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.One<E> {
            return WrapperSingle(wrapped.doOnBeforeError(consumer))
        }

        override fun indexOf(element: E): IStream.One<Int> {
            return WrapperSingle(wrapped.map { if (it == element) 0 else -1 })
        }
    }

    inner class WrapperMaybe<E>(override val wrapped: Maybe<E>) : ReaktiveWrapper<E>(), IStream.ZeroOrOne<E> {
        override fun convert(converter: IStreamBuilder): IStream.ZeroOrOne<E> {
            require(converter == this@ReaktiveStreamBuilder)
            return this
        }
        override fun wrappedAsObservable(): Observable<E> = wrapped.asObservable()
        override fun wrappedAsSingle(): Single<E> = wrapped.asSingleOrError()
        override fun wrappedAsMaybe(): Maybe<E> = wrapped
        override fun wrappedAsCompletable(): Completable = wrapped.asCompletable()

        override fun exceptionIfEmpty(exception: () -> Throwable): IStream.One<E> {
            return WrapperSingle(wrapped.asSingleOrError(exception))
        }

        override fun <R> flatMapZeroOrOne(mapper: (E) -> IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
            return WrapperMaybe(wrapped.flatMap { mapper(it).toReaktive() })
        }

        override fun filter(predicate: (E) -> Boolean): IStream.ZeroOrOne<E> {
            return WrapperMaybe(wrapped.filter(predicate))
        }

        override fun <R> map(mapper: (E) -> R): IStream.ZeroOrOne<R> {
            return WrapperMaybe(wrapped.map(mapper))
        }

        override fun ifEmpty_(defaultValue: () -> E): IStream.One<E> {
            return WrapperSingle(wrapped.asSingle(defaultValue))
        }

        override fun orNull(): IStream.One<E?> {
            return WrapperSingle(wrapped.orNull())
        }

        override fun asFlow(): Flow<E> {
            throw UnsupportedOperationException()
            // return wrapped.asObservable().asFlow()
        }

        override fun asSequence(): Sequence<E> {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun iterateBlocking(visitor: (E) -> Unit) {
            throw UnsupportedOperationException("Use IStreamExecutor.iterate")
        }

        override fun <R> flatMap(mapper: (E) -> IStream.Many<R>): IStream.Many<R> {
            return WrapperMany(wrapped.flatMapObservable { mapper(it).toReaktive() })
        }

        override fun concat(other: IStream.Many<E>): IStream.Many<E> {
            return WrapperMany(wrapped.asObservable().concatWith(other.toReaktive()))
        }

        override fun distinct(): IStream.Many<E> {
            return this // there is never more than one element
        }

        override fun assertEmpty(message: (E) -> String): IStream.Completable {
            return WrapperCompletable(wrapped.assertEmpty(message))
        }

        override fun assertNotEmpty(message: () -> String): IStream.One<E> {
            return WrapperSingle(
                wrapped.asSingleOrError {
                    throw StreamAssertionError("At least one element was expected. xxx " + message())
                },
            )
        }

        override fun drainAll(): IStream.Completable {
            return WrapperCompletable(wrapped.asCompletable())
        }

        override fun <R> fold(initial: R, operation: (R, E) -> R): IStream.One<R> {
            return WrapperSingle(wrapped.asObservable().fold(initial, operation))
        }

        override fun <K, V> toMap(
            keySelector: (E) -> K,
            valueSelector: (E) -> V,
        ): IStream.One<Map<K, V>> {
            return WrapperSingle(wrapped.asObservable().toMap(keySelector, valueSelector))
        }

        override fun <R> splitMerge(
            predicate: (E) -> Boolean,
            merger: (IStream.Many<E>, IStream.Many<E>) -> IStream.Many<R>,
        ): IStream.Many<R> {
            TODO("Not yet implemented")
        }

        override fun filterBySingle(condition: (E) -> IStream.One<Boolean>): IStream.Many<E> {
            return WrapperMany(wrapped.asObservable().filterBySingle { condition(it).toReaktive() })
        }

        override fun firstOrDefault(defaultValue: () -> E): IStream.One<E> {
            return WrapperSingle(wrapped.asSingle(defaultValue))
        }

        override fun firstOrEmpty(): IStream.ZeroOrOne<E> {
            return this
        }

        override fun switchIfEmpty_(alternative: () -> IStream.Many<E>): IStream.Many<E> {
            return WrapperMany(wrapped.asObservable().switchIfEmpty { alternative().toReaktive() })
        }

        override fun withIndex(): IStream.Many<IndexedValue<E>> {
            return WrapperMaybe(wrapped.map { IndexedValue(0, it) })
        }

        override fun onErrorReturn(valueSupplier: (Throwable) -> E): IStream.ZeroOrOne<E> {
            return WrapperMaybe(wrapped.onErrorReturn(valueSupplier))
        }

        override fun doOnBeforeError(consumer: (Throwable) -> Unit): IStream.ZeroOrOne<E> {
            return WrapperMaybe(wrapped.doOnBeforeError(consumer))
        }

        override fun indexOf(element: E): IStream.One<Int> {
            return WrapperSingle(wrapped.map { if (it == element) 0 else -1 }.defaultIfEmpty(-1))
        }
    }
    fun <R> IStream.One<R>.toReaktive() = this@ReaktiveStreamBuilder.convert(this)
    fun <R> IStream.ZeroOrOne<R>.toReaktive() = this@ReaktiveStreamBuilder.convert(this)
    fun <R> IStream.Many<R>.toReaktive() = this@ReaktiveStreamBuilder.convert(this)
    fun IStream.Completable.toReaktive() = this@ReaktiveStreamBuilder.convert(this)
}
