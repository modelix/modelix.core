package org.modelix.model.api

actual class ContextValue<E> {
    private var defaultValue: E? = null
    private val value = ThreadLocal<MutableList<E>>()

    actual constructor()
    actual constructor(defaultValue: E) {
        this.defaultValue = defaultValue
    }

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

    actual fun getValue(): E? {
        val stack: List<E> = stack
        return if (stack.isEmpty()) defaultValue else stack[stack.size - 1]
    }

    val allValues: Iterable<E>
        get() = stack
}
