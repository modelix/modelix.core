package org.modelix.streams

interface IStreamExecutor {
    fun <T> query(body: () -> IStream.One<T>): T
    suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T

    fun execute(streamProvider: () -> IStream.Zero) =
        query { streamProvider().asOne() }
    suspend fun executeSuspending(streamProvider: () -> IStream.Zero) =
        querySuspending { streamProvider().asOne() }

    fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit)
    suspend fun <T> iterateSuspending(streamProvider: suspend () -> IStream.Many<T>, visitor: suspend (T) -> Unit)

    fun <T> queryLater(body: () -> IStream.One<T>): IExecutableStream.One<T> = ExecutableStreamOne(this, body)
    fun <T> queryManyLater(body: () -> IStream.Many<T>): IExecutableStream.Many<T> = ExecutableStreamMany(this, body)
    fun <T> querySuspendingLater(body: suspend () -> IStream.One<T>): IExecutableStream.One<T> =
        ExecutableStreamOneSuspending(this, body)
    fun <T> queryManySuspendingLater(body: suspend () -> IStream.Many<T>): IExecutableStream.Many<T> =
        ExecutableStreamManySuspending(this, body)

    companion object {
        fun getInstance(): IStreamExecutor {
            return ContextStreamBuilder.globalInstance.contextValue.getValue().getStreamExecutor()
        }
    }
}

interface IStreamExecutorProvider {
    fun getStreamExecutor(): IStreamExecutor
}

class SimpleStreamExecutorProvider(private val executor: IStreamExecutor) : IStreamExecutorProvider {
    override fun getStreamExecutor(): IStreamExecutor = executor
}

fun IStreamExecutor.asProvider(): IStreamExecutorProvider = SimpleStreamExecutorProvider(this)

class ExecutorWithBuilder(val executor: IStreamExecutor, val streamBuilder: IStreamBuilder) : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        return IStream.useBuilder(streamBuilder) {
            executor.query(body)
        }
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        return IStream.useBuilderSuspending(streamBuilder) {
            executor.querySuspending(body)
        }
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        IStream.useBuilder(streamBuilder) {
            executor.iterate(streamProvider, visitor)
        }
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        IStream.useBuilderSuspending(streamBuilder) {
            executor.iterateSuspending(streamProvider, visitor)
        }
    }
}

fun IStreamExecutor.withBuilder(builder: IStreamBuilder): IStreamExecutor = ExecutorWithBuilder(this, builder)
