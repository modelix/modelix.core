package org.modelix.modelql.core

actual class AtomicLong actual constructor(initial: Long) {
    private var value: Long = initial
    actual fun incrementAndGet(): Long {
        return ++value
    }
}
