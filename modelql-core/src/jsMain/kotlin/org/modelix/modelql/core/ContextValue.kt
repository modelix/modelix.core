package org.modelix.modelql.core

actual class ContextValue<E : Any> {
    private var stack = ArrayList<E>()

    actual fun getValue(): E {
        return tryGetValue() ?: throw IllegalStateException("no value available")
    }

    actual fun tryGetValue(): E? {
        return stack.lastOrNull()
    }

    actual fun <T> computeWith(newValue: E, r: () -> T): T {
        stack.add(newValue)
        try {
            return r()
        } finally {
            stack.removeLast()
        }
    }

    actual fun getStack(): List<E> {
        return stack.toList()
    }
}
