package org.modelix.kotlin.utils

actual inline fun <R> runSynchronized(lock: Any, block: () -> R): R {
    return synchronized(lock, block)
}
