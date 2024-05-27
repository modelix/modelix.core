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

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

actual class ContextValue<E>(private val initialStack: List<E>) {

    private val valueStack = ThreadLocal.withInitial { initialStack }

    actual constructor() : this(emptyList())

    actual constructor(defaultValue: E) : this(listOf(defaultValue))

    actual fun <T> computeWith(newValue: E, body: () -> T): T {
        val oldStack: List<E> = valueStack.get()
        return try {
            valueStack.set(oldStack + newValue)
            body()
        } finally {
            valueStack.set(oldStack)
        }
    }

    actual suspend fun <T> runInCoroutine(newValue: E, body: suspend () -> T): T {
        return withContext(valueStack.asContextElement(getAllValues() + newValue)) {
            body()
        }
    }

    actual fun getValue(): E {
        val stack = valueStack.get()
        check(stack.isNotEmpty()) { "No value provided for ContextValue" }
        return stack.last()
    }

    actual fun getValueOrNull(): E? {
        return valueStack.get().lastOrNull()
    }

    actual fun getAllValues(): List<E> {
        return valueStack.get()
    }
}
