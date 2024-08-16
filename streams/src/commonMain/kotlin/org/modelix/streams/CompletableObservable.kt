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

import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.SingleObserver
import kotlin.jvm.Synchronized

class CompletableObservable<E> : Single<E> {
    private var value: E? = null
    private var throwable: Throwable? = null
    private var done: Boolean = false
    private var observers: List<SingleObserver<E>> = emptyList()

    @Synchronized
    fun isDone() = done

    @Synchronized
    fun complete(newValue: E) {
        check(!done) { "Already done" }
        value = newValue
        done = true
        for (observer in observers) {
            try {
                observer.onSuccess(newValue)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @Synchronized
    fun failed(ex: Throwable) {
        check(!done) { "Already done" }
        throwable = ex
        done = true
        for (observer in observers) {
            try {
                observer.onError(ex)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @Synchronized
    override fun subscribe(observer: SingleObserver<E>) {
        if (done) {
            if (throwable == null) {
                observer.onSuccess(value as E)
            } else {
                observer.onError(throwable!!)
            }
        } else {
            observers += observer
            observer.onSubscribe(object : Disposable {
                override fun dispose() {
                    removeObserver(observer)
                    isDisposed = true
                }
                override var isDisposed: Boolean = false
            })
        }
    }

    @Synchronized
    private fun removeObserver(observer: SingleObserver<E>) {
        observers -= observer
    }
}