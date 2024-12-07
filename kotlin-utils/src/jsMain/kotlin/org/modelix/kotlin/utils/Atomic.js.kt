package org.modelix.kotlin.utils

actual class AtomicLong actual constructor(initial: Long) {
    private var value: Long = initial
    actual fun incrementAndGet(): Long {
        return ++value
    }

    actual fun get(): Long {
        return value
    }

    actual fun set(newValue: Long) {
        value = newValue
    }

    actual fun addAndGet(delta: Long): Long {
        value += delta
        return value
    }
}

actual class AtomicBoolean actual constructor(initial: Boolean) {
    private var value: Boolean = initial
    actual fun get(): Boolean {
        return value
    }

    actual fun set(newValue: Boolean) {
        value = newValue
    }

    actual fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean {
        if (value == expectedValue) {
            value = newValue
            return true
        } else {
            return false
        }
    }

    actual fun getAndSet(newValue: Boolean): Boolean {
        val oldValue = value
        value = newValue
        return oldValue
    }
}
