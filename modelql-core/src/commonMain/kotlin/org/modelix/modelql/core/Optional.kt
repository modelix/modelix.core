package org.modelix.modelql.core

import kotlin.jvm.JvmInline

@JvmInline
value class Optional<out E>(private val value: Any?) {
    fun isPresent(): Boolean = value != EMPTY
    fun get(): E {
        require(isPresent()) { "Optional value is not present" }
        return value as E
    }
    fun <R> map(body: (E) -> R): Optional<R> = if (isPresent()) of(body(get())) else empty()
    fun <R> flatMap(body: (E) -> Optional<R>): Optional<R> = if (isPresent()) body(get()) else empty()
    companion object {
        private object EMPTY
        fun <T> empty() = Optional<T>(EMPTY)
        fun <T> of(value: T) = Optional<T>(value)
    }
}

fun <T> Optional<T>.getOrElse(defaultValue: T): T = if (isPresent()) get() else defaultValue
fun <T> Optional<T>.presentAndEqual(other: T): Boolean = isPresent() && get() == other
