/*
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
package org.modelix.kotlin.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

actual class ContextValue<E> {
    private val initialStack: List<E>
    private var synchronousValueStack: List<E>? = null
    private var stackFromCoroutine: List<E>? = null
    private val contextElementKey = object : CoroutineContext.Key<ContextValueElement<E>> {}
    private var isInSynchronousBlock = false

    actual constructor() {
        initialStack = emptyList()
    }

    actual constructor(defaultValue: E) {
        initialStack = listOf(defaultValue)
    }

    actual fun getValue(): E {
        val stack = getAllValues()
        check(stack.isNotEmpty()) { "No value provided for ContextValue" }
        return stack.last()
    }

    actual fun getValueOrNull(): E? {
        return getAllValues().lastOrNull()
    }

    actual fun <T> computeWith(newValue: E, body: () -> T): T {
        val oldStack = synchronousValueStack
        val newStack = getAllValues() + newValue
        val wasInSynchronousBlock = isInSynchronousBlock
        try {
            isInSynchronousBlock = true
            synchronousValueStack = newStack
            return body()
        } finally {
            synchronousValueStack = oldStack
            isInSynchronousBlock = wasInSynchronousBlock
        }
    }

    actual fun getAllValues(): List<E> {
        return (if (isInSynchronousBlock) synchronousValueStack else stackFromCoroutine) ?: initialStack
    }

    private suspend fun getAllValuesFromCoroutine(): List<E>? {
        return currentCoroutineContext()[contextElementKey]?.stack
    }

    actual suspend fun <T> runInCoroutine(newValue: E, body: suspend () -> T): T {
        return withContext(ContextValueElement((getAllValuesFromCoroutine() ?: initialStack) + newValue)) {
            val parentInterceptor = checkNotNull(currentCoroutineContext()[ContinuationInterceptor]) {
                "No ContinuationInterceptor found in the context"
            }
            withContext(Interceptor(parentInterceptor)) {
                body()
            }
        }
    }

    private inner class Interceptor(
        private val dispatcher: ContinuationInterceptor,
    ) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
            return dispatcher.interceptContinuation(object : Continuation<T> {
                override val context get() = continuation.context

                override fun resumeWith(result: Result<T>) {
                    stackFromCoroutine = context[contextElementKey]?.stack ?: emptyList()
                    continuation.resumeWith(result)
                }
            })
        }

        override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
            super.releaseInterceptedContinuation(continuation)
            stackFromCoroutine = null
        }
    }

    inner class ContextValueElement<E>(val stack: List<E>) : AbstractCoroutineContextElement(contextElementKey)
}
