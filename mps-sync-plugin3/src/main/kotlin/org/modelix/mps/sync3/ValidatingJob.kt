package org.modelix.mps.sync3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private val LOG = mu.KotlinLogging.logger { }

class ValidatingJob(private val validate: suspend () -> Unit) {
    private val dirty = Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)

    fun invalidate() {
        dirty.trySend(Unit)
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
