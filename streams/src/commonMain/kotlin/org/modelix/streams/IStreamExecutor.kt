package org.modelix.streams

import org.modelix.kotlin.utils.ContextValue

interface IStreamExecutor {
    fun <T> query(body: () -> IStream.One<T>): T
    suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T

    fun execute(streamProvider: () -> IStream.Completable) =
        query { streamProvider().andThenUnit() }
    suspend fun executeSuspending(streamProvider: () -> IStream.Completable) =
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
fun IStreamExecutorProvider.execute(streamProvider: () -> IStream.Completable) =
    query { streamProvider().andThenUnit() }
suspend fun IStreamExecutorProvider.executeSuspending(streamProvider: () -> IStream.Completable) =
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

// fun <T> IStream.One<T>.getSynchronous(executor: IStreamExecutorProvider): T = getSynchronous(executor.getStreamExecutor())
// fun <T> IStream.One<T>.getSynchronous(executor: IStreamExecutor): T = executor.query { this }

fun <T> IStream.One<T>.getBlocking(executor: IStreamExecutorProvider): T = getBlocking(executor.getStreamExecutor())
fun <T> IStream.One<T>.getBlocking(executor: IStreamExecutor): T = executor.query { this }

fun <T> IStream.ZeroOrOne<T>.getBlocking(executor: IStreamExecutorProvider): T? = getBlocking(executor.getStreamExecutor())
fun <T> IStream.ZeroOrOne<T>.getBlocking(executor: IStreamExecutor): T? = executor.query { this.orNull() }

suspend fun <T> IStream.One<T>.getSuspending(executor: IStreamExecutorProvider): T = getSuspending(executor.getStreamExecutor())
suspend fun <T> IStream.One<T>.getSuspending(executor: IStreamExecutor): T = executor.querySuspending { this }

suspend fun <T> IStream.ZeroOrOne<T>.getSuspending(executor: IStreamExecutorProvider): T? = getSuspending(executor.getStreamExecutor())
suspend fun <T> IStream.ZeroOrOne<T>.getSuspending(executor: IStreamExecutor): T? = executor.querySuspending { this.orNull() }

// fun <T> IStream.Many<T>.iterateSynchronous(executor: IStreamExecutorProvider, visitor: (T) -> Unit): Unit = iterateSynchronous(executor.getStreamExecutor(), visitor)
// fun <T> IStream.Many<T>.iterateSynchronous(executor: IStreamExecutor, visitor: (T) -> Unit): Unit = executor.iterate({ this }, visitor)

fun <T> IStream.Many<T>.iterateBlocking(executor: IStreamExecutorProvider, visitor: (T) -> Unit): Unit = iterateBlocking(executor.getStreamExecutor(), visitor)
fun <T> IStream.Many<T>.iterateBlocking(executor: IStreamExecutor, visitor: (T) -> Unit): Unit = executor.iterate({ this }, visitor)

suspend fun <T> IStream.Many<T>.iterateSuspending(executor: IStreamExecutorProvider, visitor: suspend (T) -> Unit): Unit = iterateSuspending(executor.getStreamExecutor(), visitor)
suspend fun <T> IStream.Many<T>.iterateSuspending(executor: IStreamExecutor, visitor: suspend (T) -> Unit): Unit = executor.iterateSuspending({ this }, visitor)
