package org.modelix.kotlin.utils

import java.lang.ThreadLocal as JvmThreadLocal

actual class ThreadLocal<E> actual constructor(initialValueSupplier: () -> E) {

    private val threadLocal = JvmThreadLocal.withInitial(initialValueSupplier)

    actual fun get(): E {
        return threadLocal.get()
    }

    actual fun set(value: E) {
        return threadLocal.set(value)
    }

    actual fun remove() {
        return threadLocal.remove()
    }
}
