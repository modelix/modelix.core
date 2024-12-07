package org.modelix.model.api

actual inline fun <R> runSynchronized(lock: Any, block: () -> R): R {
    return block()
}
