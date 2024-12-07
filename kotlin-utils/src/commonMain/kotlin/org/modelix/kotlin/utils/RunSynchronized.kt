package org.modelix.kotlin.utils

expect inline fun <R> runSynchronized(lock: Any, block: () -> R): R
