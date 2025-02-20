package org.modelix.mps.sync3

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ValueWithMutex<E>(private var value: E) {
    private val mutex = Mutex()
    private var lastUpdateResult: Result<E>? = null

    suspend fun <R : E> updateValue(body: suspend (E) -> R): R {
        return mutex.withLock {
            val newValue = runCatching {
                body(value)
            }
            lastUpdateResult = newValue
            newValue.onFailure {
                BindingWorker.Companion.LOG.error(it) { "Value update failed. Keeping $value" }
            }
            newValue.getOrThrow().also { value = it }
        }
    }

    /**
     * Blocks until any active update is done.
     * @return The result of the most recent update attempt.
     */
    suspend fun flush(): Result<E>? {
        return mutex.withLock { lastUpdateResult }
    }

    fun isLocked() = mutex.isLocked

    fun getValue(): E = value
}
