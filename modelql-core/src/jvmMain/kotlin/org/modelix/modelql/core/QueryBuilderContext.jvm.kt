package org.modelix.modelql.core

actual class ContextValue<E : Any> {
    private val value = ThreadLocal<MutableList<E>>()

    private val stack: MutableList<E>
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
            stack.add(newValue)
            r()
        } finally {
            val stack: MutableList<E> = stack
            stack.removeAt(stack.size - 1)
        }
    }

    actual fun getValue(): E {
        val stack: List<E> = stack
        return if (stack.isEmpty()) throw IllegalStateException("no value available") else stack[stack.size - 1]
    }

    val allValues: Iterable<E>
        get() = stack
}