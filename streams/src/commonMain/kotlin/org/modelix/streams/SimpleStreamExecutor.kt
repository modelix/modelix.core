package org.modelix.streams

import org.modelix.streams.engine.Execution
import org.modelix.streams.engine.drive
import org.modelix.streams.engine.driveSuspending

/**
 * Default executor. Drives streams with no batch-size limit. Fetches embedded in a stream (via
 * [BulkRequestStreamExecutor.enqueue]) are still batched per source per round, so this is safe to use even when a
 * stream contains data requests; it simply doesn't impose a maximum batch size.
 */
object SimpleStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        val execution = Execution()
        return execution.drive(body().asStep(execution), Int.MAX_VALUE).single()
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        val execution = Execution()
        return execution.driveSuspending(body().asStep(execution), Int.MAX_VALUE).single()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        val execution = Execution()
        execution.drive(streamProvider().asStep(execution), Int.MAX_VALUE).forEach(visitor)
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        val execution = Execution()
        execution.driveSuspending(streamProvider().asStep(execution), Int.MAX_VALUE).forEach { visitor(it) }
    }
}
