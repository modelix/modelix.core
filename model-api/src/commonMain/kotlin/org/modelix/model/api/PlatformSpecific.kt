package org.modelix.model.api

expect inline fun <R> runSynchronized(lock: Any, block: () -> R): R
