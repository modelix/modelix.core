package org.modelix.streams

import org.modelix.kotlin.utils.ContextValue

interface IStreamExecutor {
    fun <T> query(body: () -> IStream.One<T>): T
    suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T

    fun execute(streamProvider: () -> IStream.Zero) =
        query { streamProvider().andThenUnit() }
    suspend fun executeSuspending(streamProvider: () -> IStream.Zero) =
        querySuspending { streamProvider().andThenUnit() }

    fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit)
    suspend fun <T> iterateSuspending(streamProvider: suspend () -> IStream.Many<T>, visitor: suspend (T) -> Unit)

    fun <T> queryLater(body: () -> IStream.One<T>): IExecutableStream.One<T> = ExecutableStreamOne(this, body)
    fun <T> queryManyLater(body: () -> IStream.Many<T>): IExecutableStream.Many<T> = ExecutableStreamMany(this, body)
    fun <T> querySuspendingLater(body: suspend () -> IStream.One<T>): IExecutableStream.One<T> =
        ExecutableStreamOneSuspending(this, body)
    fun <T> queryManySuspendingLater(body: suspend () -> IStream.Many<T>): IExecutableStream.Many<T> =
        ExecutableStreamManySuspending(this, body)

    companion object {
        val CONTEXT = ContextValue<IStreamExecutor>()
        fun getInstance(): IStreamExecutor {
            return CONTEXT.getValueOrNull() ?: SimpleStreamExecutor
        }
    }
}

fun <T> IStreamExecutorProvider.query(body: () -> IStream.One<T>): T = getStreamExecutor().query(body)
suspend fun <T> IStreamExecutorProvider.querySuspending(body: suspend () -> IStream.One<T>): T =
    getStreamExecutor().querySuspending(body)
fun IStreamExecutorProvider.execute(streamProvider: () -> IStream.Zero) =
    query { streamProvider().andThenUnit() }
suspend fun IStreamExecutorProvider.executeSuspending(streamProvider: () -> IStream.Zero) =
    querySuspending { streamProvider().andThenUnit() }
fun <T> IStreamExecutorProvider.iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) =
    getStreamExecutor().iterate(streamProvider, visitor)
suspend fun <T> IStreamExecutorProvider.iterateSuspending(streamProvider: suspend () -> IStream.Many<T>, visitor: suspend (T) -> Unit) =
    getStreamExecutor().iterateSuspending(streamProvider, visitor)
fun <T> IStreamExecutorProvider.queryLater(body: () -> IStream.One<T>): IExecutableStream.One<T> =
    getStreamExecutor().queryLater(body)
fun <T> IStreamExecutorProvider.queryManyLater(body: () -> IStream.Many<T>): IExecutableStream.Many<T> =
    getStreamExecutor().queryManyLater(body)
fun <T> IStreamExecutorProvider.querySuspendingLater(body: suspend () -> IStream.One<T>): IExecutableStream.One<T> =
    getStreamExecutor().querySuspendingLater(body)
fun <T> IStreamExecutorProvider.queryManySuspendingLater(body: suspend () -> IStream.Many<T>): IExecutableStream.Many<T> =
    getStreamExecutor().queryManySuspendingLater(body)

interface IStreamExecutorProvider {
    fun getStreamExecutor(): IStreamExecutor
}

class SimpleStreamExecutorProvider(private val executor: IStreamExecutor) : IStreamExecutorProvider {
    override fun getStreamExecutor(): IStreamExecutor = executor
}

fun IStreamExecutor.asProvider(): IStreamExecutorProvider = SimpleStreamExecutorProvider(this)
