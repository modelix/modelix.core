package org.modelix.modelql.core

expect class AtomicLong(initial: Long) {
    fun incrementAndGet(): Long
}
