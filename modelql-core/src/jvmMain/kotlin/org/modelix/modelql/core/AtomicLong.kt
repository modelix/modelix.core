package org.modelix.modelql.core

actual class AtomicLong actual constructor(initial: Long) {
    private val value = java.util.concurrent.atomic.AtomicLong(initial)
    actual fun incrementAndGet(): Long {
        return value.incrementAndGet()
    }
}
