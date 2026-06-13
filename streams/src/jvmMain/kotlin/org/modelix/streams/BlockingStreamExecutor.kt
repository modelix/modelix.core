package org.modelix.streams

import org.modelix.streams.engine.Execution
import org.modelix.streams.engine.drive
import org.modelix.streams.engine.driveSuspending

/**
 * JVM executor that always drives streams to completion blocking, resolving async leaves (e.g. flows) via
 * [org.modelix.kotlin.utils.runBlockingIfJvm] inside the engine. With the unified engine this behaves like
 * [SimpleStreamExecutor]; it is retained for API compatibility.
 */
object BlockingStreamExecutor : IStreamExecutor {
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
