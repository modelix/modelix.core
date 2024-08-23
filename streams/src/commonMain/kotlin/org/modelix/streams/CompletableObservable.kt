/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.streams

import com.badoo.reaktive.single.SingleEmitter
import com.badoo.reaktive.single.single
import org.modelix.kotlin.utils.runSynchronized

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
