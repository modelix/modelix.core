package org.modelix.streams

import org.modelix.streams.engine.Execution

/**
 * Default executor. Fetches embedded in a stream (via [BulkRequestStreamExecutor.enqueue]) are batched per source per
 * round, chunked to each source's [IBulkExecutor.batchSize], so this is safe to use even when a stream contains data
 * requests.
 */
object SimpleStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        val execution = Execution()
        return execution.drive(body().asStep(execution)).single()
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        val execution = Execution()
        return execution.driveSuspending(body().asStep(execution)).single()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        val execution = Execution()
        execution.drive(streamProvider().asStep(execution)).forEach(visitor)
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        val execution = Execution()
        execution.driveSuspending(streamProvider().asStep(execution)).forEach { visitor(it) }
    }
}
