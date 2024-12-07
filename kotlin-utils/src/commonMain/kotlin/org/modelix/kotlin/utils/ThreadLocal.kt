package org.modelix.kotlin.utils

expect class ThreadLocal<E>(initialValueSupplier: () -> E) {
    fun get(): E
    fun set(value: E)
    fun remove()
}
