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
        val stack: List<E> = internalStack
        return if (stack.isEmpty()) throw IllegalStateException("no value available") else stack[stack.size - 1]
    }

    actual fun getStack(): List<E> {
        return internalStack.toList()
    }
}
