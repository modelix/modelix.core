package org.modelix.kotlin.utils

import kotlinx.coroutines.runBlocking

actual fun <R> runBlockingIfJvm(body: suspend () -> R): R {
    return runBlocking {
        body()
    }
}
