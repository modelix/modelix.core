package org.modelix.kotlin.utils

expect class AtomicLong(initial: Long) {
    fun incrementAndGet(): Long
    fun get(): Long
    fun set(newValue: Long)
    fun addAndGet(delta: Long): Long
}

expect class AtomicBoolean(initial: Boolean) {
    fun get(): Boolean
    fun set(newValue: Boolean)
    fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean
    fun getAndSet(newValue: Boolean): Boolean
}
