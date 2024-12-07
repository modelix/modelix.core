package org.modelix.kotlin.utils

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

actual class ContextValue<E>(private val initialStack: List<E>) {

    private val valueStack = java.lang.ThreadLocal.withInitial { initialStack }

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
