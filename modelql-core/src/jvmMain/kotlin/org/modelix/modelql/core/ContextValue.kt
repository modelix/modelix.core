/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.core

actual class ContextValue<E : Any> {
    private val value = ThreadLocal<MutableList<E>>()

    private val internalStack: MutableList<E>
        get() {
            var stack = value.get()
            if (stack == null) {
                stack = ArrayList()
                value.set(stack)
            }
            return stack
        }

    actual fun <T> computeWith(newValue: E, r: () -> T): T {
        return try {
            internalStack.add(newValue)
            r()
        } finally {
            val stack: MutableList<E> = internalStack
            stack.removeAt(stack.size - 1)
        }
    }

    actual fun getValue(): E {
        return tryGetValue() ?: throw IllegalStateException("no value available")
    }

    actual fun tryGetValue(): E? {
        val stack: List<E> = internalStack
        return if (stack.isEmpty()) null else stack[stack.size - 1]
    }

    actual fun getStack(): List<E> {
        return internalStack.toList()
    }
}
