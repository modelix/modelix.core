package org.modelix.kotlin.utils

expect fun <R> runBlockingIfJvm(body: suspend () -> R): R
