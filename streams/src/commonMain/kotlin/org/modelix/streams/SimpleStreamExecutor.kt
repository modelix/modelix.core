package org.modelix.streams

class SimpleStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        @Suppress("DEPRECATION")
        return body().getSynchronous()
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        @Suppress("DEPRECATION")
        return body().getSuspending()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        @Suppress("DEPRECATION")
        streamProvider().iterateSynchronous(visitor)
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        @Suppress("DEPRECATION")
        streamProvider().iterateSuspending(visitor)
    }
}
