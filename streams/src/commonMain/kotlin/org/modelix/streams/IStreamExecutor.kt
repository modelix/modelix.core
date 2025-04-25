package org.modelix.streams

import org.modelix.kotlin.utils.ContextValue

/**
 * There reason that there are three different types of implementations if they all have different execution semantics.
 *
 * - Sequence based streams provide the best performance in cases where streams aren't necessary, but the API is based
 *   on streams. This is the case when all the data is locally available and no requests have to be made.
 * - Flow based streams provide the best integration with data sources that use suspendable functions.
 * - Reaktive based streams support a push semantics, while sequences and flows only support a pull semantics. The push
 *   execution semantics are required for bundling multiple requests into larger bulk requests.
 *   Reaktive subscribes to all individual requests in parallel and then processed the result after the bulk response
 *   was received and pushed into the streams. Sequences and flows would wait for each result of each individual request
 *   before enqueuing the next. Flows could in theory parallelize the requests, but that would create lots of coroutines
 *   with much more overhead than the Reaktive streams.
 *
 * # Performance
 * Streams are used because they provide a programming model for asynchronous execution flows. The overhead though is
 * significant. That's why we try to avoid them and evaluate them eagerly where possible.
 *
 * For example, if the source of a stream is a constant value and a map operation is executed on it, the mapping is
 * evaluated immediately and a new constant value stream is created. If all the data is already available locally,
 * then all streams are evaluated eagerly with minimal overhead.
 *
 * It's important to know that eagerly evaluating operations on collections is often faster than using sequences.
 * For most people this is unintuitive, and they expect a chain of operations to perform better when using sequence and
 * avoiding the intermediate collections, but the optimizations on hardware level work better with collections.
 * Sequences only show a performance benefit on large collections.
 */
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
