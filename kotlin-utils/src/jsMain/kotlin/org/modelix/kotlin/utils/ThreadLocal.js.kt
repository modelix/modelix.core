package org.modelix.kotlin.utils

actual class ThreadLocal<E> actual constructor(val initialValueSupplier: () -> E) {

    private var value: E = initialValueSupplier()

    actual fun get(): E {
        return value
    }

    actual fun set(value: E) {
        this.value = value
    }

    actual fun remove() {
        this.value = initialValueSupplier()
    }
}
