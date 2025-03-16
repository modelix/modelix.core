package org.modelix.streams

object FailingStreamExecutor : IStreamExecutor {
    private fun fail(): Nothing = throw UnsupportedOperationException("Bulk requests not supported. Use some wrapper that supports it.")
    override fun <T> query(body: () -> IStream.One<T>): T = fail()
    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T = fail()
    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) = fail()
    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) = fail()
    override fun <T> queryLater(body: () -> IStream.One<T>): IExecutableStream.One<T> = fail()
    override fun <T> queryManyLater(body: () -> IStream.Many<T>): IExecutableStream.Many<T> = fail()
    override fun <T> querySuspendingLater(body: suspend () -> IStream.One<T>): IExecutableStream.One<T> = fail()
    override fun <T> queryManySuspendingLater(body: suspend () -> IStream.Many<T>): IExecutableStream.Many<T> = fail()
}
