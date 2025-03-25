package org.modelix.streams

interface IExecutableStream : IStreamExecutorProvider {
    interface One<out E> : IExecutableStream {
        fun <R> mapOne(transformation: (IStream.One<E>) -> IStream.One<R>): One<R>
        fun <R> mapMany(transformation: (IStream.One<E>) -> IStream.Many<R>): Many<R>
        fun query(): E
        suspend fun querySuspending(): E
    }
    interface Many<out E> : IExecutableStream {
        fun <R> mapMany(transformation: (IStream.Many<E>) -> IStream.Many<R>): Many<R>
        fun <R> mapOne(transformation: (IStream.Many<E>) -> IStream.One<R>): One<R>
        fun iterate(visitor: (E) -> Unit)
        suspend fun iterateSuspending(visitor: suspend (E) -> Unit)
    }

    companion object {
        fun <T> of(element: T): IExecutableStream.One<T> {
            return ExecutableStreamOne(SimpleStreamExecutor) { IStream.of(element) }
        }
        fun <T> many(vararg elements: T): IExecutableStream.Many<T> {
            return ExecutableStreamMany(SimpleStreamExecutor) { IStream.many(elements) }
        }
        fun <T> many(elements: Sequence<T>): IExecutableStream.Many<T> {
            return ExecutableStreamMany(SimpleStreamExecutor) { IStream.many(elements) }
        }
        fun <T> many(elements: Iterable<T>): IExecutableStream.Many<T> {
            return ExecutableStreamMany(SimpleStreamExecutor) { IStream.many(elements) }
        }
    }
}

class ExecutableStreamOne<T>(
    private val executor: IStreamExecutor,
    private val streamBuilder: () -> IStream.One<T>,
) : IExecutableStream.One<T> {
    override fun getStreamExecutor(): IStreamExecutor = executor

    override fun <R> mapOne(transformation: (IStream.One<T>) -> IStream.One<R>): IExecutableStream.One<R> {
        return ExecutableStreamOne(executor) { transformation(streamBuilder()) }
    }

    override fun <R> mapMany(transformation: (IStream.One<T>) -> IStream.Many<R>): IExecutableStream.Many<R> {
        return ExecutableStreamMany(executor) { transformation(streamBuilder()) }
    }

    override fun query(): T = executor.query(streamBuilder)
    override suspend fun querySuspending(): T = executor.querySuspending { streamBuilder() }
}

fun <T> IStream.One<T>.asExecutable(executor: IStreamExecutor) = ExecutableStreamOne(executor) { this }
fun <T> IStream.Many<T>.asExecutable(executor: IStreamExecutor) = ExecutableStreamMany(executor) { this }

class ExecutableStreamOneSuspending<T>(
    private val executor: IStreamExecutor,
    private val streamBuilder: suspend () -> IStream.One<T>,
) : IExecutableStream.One<T> {

    override fun getStreamExecutor(): IStreamExecutor = executor

    override fun <R> mapOne(transformation: (IStream.One<T>) -> IStream.One<R>): IExecutableStream.One<R> {
        return ExecutableStreamOneSuspending(executor) { transformation(streamBuilder()) }
    }

    override fun <R> mapMany(transformation: (IStream.One<T>) -> IStream.Many<R>): IExecutableStream.Many<R> {
        return ExecutableStreamManySuspending(executor) { transformation(streamBuilder()) }
    }

    override fun query(): T = throw UnsupportedOperationException("Use querySuspending")
    override suspend fun querySuspending(): T = executor.querySuspending { streamBuilder() }
}

class ExecutableStreamMany<T>(
    private val executor: IStreamExecutor,
    private val streamBuilder: () -> IStream.Many<T>,
) : IExecutableStream.Many<T> {

    override fun getStreamExecutor(): IStreamExecutor = executor

    override fun <R> mapMany(transformation: (IStream.Many<T>) -> IStream.Many<R>): IExecutableStream.Many<R> {
        return ExecutableStreamMany(executor) { transformation(streamBuilder()) }
    }

    override fun <R> mapOne(transformation: (IStream.Many<T>) -> IStream.One<R>): IExecutableStream.One<R> {
        return ExecutableStreamOne(executor) { transformation(streamBuilder()) }
    }

    override fun iterate(visitor: (T) -> Unit) {
        return executor.iterate(streamBuilder, visitor)
    }

    override suspend fun iterateSuspending(visitor: suspend (T) -> Unit) {
        return executor.iterateSuspending(streamBuilder, visitor)
    }
}

class ExecutableStreamManySuspending<T>(
    private val executor: IStreamExecutor,
    val streamBuilder: suspend () -> IStream.Many<T>,
) : IExecutableStream.Many<T> {

    override fun getStreamExecutor(): IStreamExecutor = executor

    override fun <R> mapMany(transformation: (IStream.Many<T>) -> IStream.Many<R>): IExecutableStream.Many<R> {
        return ExecutableStreamManySuspending(executor) { transformation(streamBuilder()) }
    }

    override fun <R> mapOne(transformation: (IStream.Many<T>) -> IStream.One<R>): IExecutableStream.One<R> {
        return ExecutableStreamOneSuspending(executor) { transformation(streamBuilder()) }
    }

    override fun iterate(visitor: (T) -> Unit) {
        return throw UnsupportedOperationException("Use iterateSuspending")
    }

    override suspend fun iterateSuspending(visitor: suspend (T) -> Unit) {
        return executor.iterateSuspending(streamBuilder, visitor)
    }
}
