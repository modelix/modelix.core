package org.modelix.streams

import com.badoo.reaktive.base.tryCatch
import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.completable.CompletableCallbacks
import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asCompletable
import com.badoo.reaktive.maybe.asObservable
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.ObservableObserver
import com.badoo.reaktive.observable.asCompletable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.autoConnect
import com.badoo.reaktive.observable.collect
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.firstOrDefault
import com.badoo.reaktive.observable.firstOrError
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observable
import com.badoo.reaktive.observable.replay
import com.badoo.reaktive.observable.switchIfEmpty
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.observable.zipWith
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.flatMap
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.subscribe

class StreamAssertionError(message: String) : IllegalArgumentException(message)

fun Observable<*>.count(): Single<Int> = collect({ arrayOf(0) }) { acc, it -> acc[0]++ }.map { it[0] }
fun <T, R> Observable<T>.fold(initial: R, operation: (R, T) -> R): Single<R> {
    return collect({ mutableListOf(initial) }) { acc, it -> acc[0] = operation(acc[0], it) }.map { it[0] }
}

fun <T> Sequence<T>.asObservable(): Observable<T> = asIterable().asObservable()
fun <T> Observable<T>.exactlyOne(): Single<T> = toList().map { it.single() }
fun <T> Observable<T>.firstOrNull(): Single<T?> = firstOrDefault(null)
fun Observable<*>.isEmpty(): Single<Boolean> = map { false }.firstOrDefault(true)
fun <T> Observable<T>.filterBySingle(condition: (T) -> Single<Boolean>): Observable<T> {
    return this.zipWith(this.flatMapSingle { condition(it) }) { it, c -> it to c }.filter { it.second }.map { it.first }
}
fun <T> Observable<T>.withIndex(): Observable<IndexedValue<T>> {
    var index = 0
    return map { IndexedValue(index++, it) }
}
fun <T> Observable<T>.assertNotEmpty(message: () -> String): Observable<T> {
    return this.switchIfEmpty { throw StreamAssertionError("At least one element was expected. xxx " + message()) }
}
fun <T> Maybe<T>.assertEmpty(message: (T) -> String): Completable {
    return map { throw StreamAssertionError(message(it)) }.asCompletable()
}
fun <T> Observable<T>.assertEmpty(message: (T) -> String): Completable {
    return map { throw StreamAssertionError(message(it)) }.asCompletable()
}
fun <T> Single<T>.cached(): Single<T> {
    return this.asObservable().replay(1).autoConnect()
        .firstOrError { IllegalStateException("Single was empty. Should not happen.") }
}

fun <T> Maybe<T>.orNull(): Single<T?> = defaultIfEmpty(null)
fun <T> Array<T>.asObservable(): Observable<T> = asIterable().asObservable()
fun LongArray.asObservable(): Observable<Long> = asIterable().asObservable()

fun <T> Observable<T>.distinct(): Observable<T> {
    return observable { emitter ->
        val emittedValues = HashSet<T>()
        subscribe(
            object : ObservableObserver<T>, CompletableCallbacks by emitter {
                override fun onSubscribe(disposable: Disposable) {
                    emitter.setDisposable(disposable)
                }

                override fun onNext(value: T) {
                    emitter.tryCatch(block = { emittedValues.add(value) }) {
                        if (it) {
                            emitter.onNext(value)
                        }
                    }
                }
            },
        )
    }
}

fun Maybe<*>.exists(): Single<Boolean> = map { true }.defaultIfEmpty(false)
fun <T> Single<Single<T>>.flatten(): Single<T> = flatMap { it }
