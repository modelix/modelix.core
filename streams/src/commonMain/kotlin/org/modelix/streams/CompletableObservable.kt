package org.modelix.streams

import com.badoo.reaktive.single.SingleEmitter
import com.badoo.reaktive.single.single
import org.modelix.kotlin.utils.runSynchronized

/**
 * Similar to a CompletableFuture, observers can wait for a value that is provided in the future by some
 * asynchronous process.
 */
class CompletableObservable<E>(val afterSubscribe: () -> Unit = {}) {
    val single = single<E> {
        runSynchronized(this) {
            if (done) {
                emitResult(it)
            } else {
                observers += it
            }
        }
        afterSubscribe()
    }
    private var value: E? = null
    private var throwable: Throwable? = null
    private var done: Boolean = false
    private var observers: List<SingleEmitter<E>> = emptyList()

    private fun emitResult(observers: List<SingleEmitter<E>>) {
        for (observer in observers) {
            emitResult(observer)
        }
    }

    private fun emitResult(emitter: SingleEmitter<E>) {
        if (throwable == null) {
            emitter.onSuccess(value as E)
        } else {
            emitter.onError(throwable!!)
        }
    }

    private fun clearObservers(): List<SingleEmitter<E>> {
        return observers.also { observers = emptyList() }
    }

    fun isDone() = runSynchronized(this) { done }

    fun complete(newValue: E) {
        emitResult(
            runSynchronized(this) {
                check(!done) { "Already done" }
                value = newValue
                done = true
                clearObservers()
            },
        )
    }

    fun failed(ex: Throwable) {
        emitResult(
            runSynchronized(this) {
                check(!done) { "Already done" }
                throwable = ex
                done = true
                clearObservers()
            },
        )
    }
}
