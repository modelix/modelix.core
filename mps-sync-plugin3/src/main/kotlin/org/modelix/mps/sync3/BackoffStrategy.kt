package org.modelix.mps.sync3

import kotlinx.coroutines.delay
import kotlin.math.roundToLong

class BackoffStrategy(
    val initialDelay: Long = 500,
    val maxDelay: Long = 10_000,
    val factor: Double = 1.5,
) {
    var currentDelay: Long = initialDelay

    fun failed() {
        currentDelay = (currentDelay * factor).roundToLong().coerceAtMost(maxDelay)
    }

    fun success() {
        currentDelay = initialDelay
    }

    suspend fun wait() {
        delay(currentDelay)
    }
}
