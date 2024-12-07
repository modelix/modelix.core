package org.modelix.kotlin.utils

actual inline fun <R> runSynchronized(lock: Any, block: () -> R): R {
    // This method is only required when compiling to the JVM. JS is single-threaded.
    return block()
}
