package org.modelix.streams

import org.modelix.kotlin.utils.DelicateModelixApi

class SimpleStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
        return body().getSynchronous()
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
        return body().getSuspending()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
        streamProvider().iterateSynchronous(visitor)
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        @OptIn(DelicateModelixApi::class) // usage inside IStreamExecutor is allowed
        streamProvider().iterateSuspending(visitor)
    }
}
