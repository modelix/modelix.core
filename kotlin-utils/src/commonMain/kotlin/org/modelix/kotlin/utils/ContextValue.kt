package org.modelix.kotlin.utils

/**
 * A common abstraction over ThreadLocal and CoroutineContext that integrates both worlds.
 * Allows to set a value that can be read from everywhere on the current thread or coroutine.
 * A suspendable function can call non suspendable functions and the value is synchronized between the CoroutineContext
 * and the internal ThreadLocal.
 */
expect class ContextValue<E> {

    constructor()
    constructor(defaultValue: E)

    /**
     * @throws NoSuchElementException if no value is set.
     */
    fun getValue(): E
    fun getValueOrNull(): E?
    fun getAllValues(): List<E>
    fun <T> computeWith(newValue: E, body: () -> T): T

    suspend fun <T> runInCoroutine(newValue: E, body: suspend () -> T): T
}

fun <E, T> ContextValue<E>.offer(value: E, body: () -> T): T {
    return if (getAllValues().isEmpty()) {
        computeWith(value, body)
    } else {
        body()
    }
}
