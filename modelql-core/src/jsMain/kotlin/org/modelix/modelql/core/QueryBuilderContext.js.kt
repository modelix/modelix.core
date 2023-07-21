package org.modelix.modelql.core

actual class ContextValue<E : Any> {
    private var value: E? = null

    actual fun getValue(): E {
        return value ?: throw IllegalStateException("no value available")
    }

    actual fun <T> computeWith(newValue: E, r: () -> T): T {
        val oldValue = value
        value = newValue
        try {
            return r()
        } finally {
            value = oldValue
        }
    }
}