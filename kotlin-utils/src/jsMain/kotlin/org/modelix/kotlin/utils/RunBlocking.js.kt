package org.modelix.kotlin.utils

actual fun <R> runBlockingIfJvm(body: suspend () -> R): R {
    throw UnsupportedOperationException("runBlocking not support by JS")
}
