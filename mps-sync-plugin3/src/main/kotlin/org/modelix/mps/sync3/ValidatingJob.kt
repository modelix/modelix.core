package org.modelix.mps.sync3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private val LOG = mu.KotlinLogging.logger { }

class ValidatingJob(private val validate: suspend () -> Unit) {
    private val dirty = Channel<Unit>(1)

    fun invalidate() {
        // can't use trySend because it doesn't exist in MPS 2020.3
        @Suppress("DEPRECATION_ERROR")
        dirty.offer(Unit)
    }

    suspend fun run() {
        jobLoop {
            dirty.receive()
            validate()
        }
    }
}

fun CoroutineScope.launchValidation(body: suspend () -> Unit): ValidatingJob {
    val job = ValidatingJob(body)
    launch {
        job.run()
    }
    return job
}
