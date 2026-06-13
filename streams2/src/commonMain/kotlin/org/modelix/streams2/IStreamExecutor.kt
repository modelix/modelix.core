package org.modelix.streams2

/**
 * Materializes streams. Each query runs an independent round loop with its own fetch cache, issuing one bulk call
 * per data source per round (see [Execution.drive]).
 */
interface IStreamExecutor {
    fun <T> query(stream: IStream.One<T>): T
    fun <T> queryAll(stream: IStream.Many<T>): List<T>
    fun <T> iterate(stream: IStream.Many<T>, visitor: (T) -> Unit)
}

class StreamExecutor : IStreamExecutor {
    override fun <T> queryAll(stream: IStream.Many<T>): List<T> {
        val execution = Execution()
        return execution.drive(stream.toStep(execution))
    }

    override fun <T> query(stream: IStream.One<T>): T = queryAll(stream).single()

    override fun <T> iterate(stream: IStream.Many<T>, visitor: (T) -> Unit) {
        for (value in queryAll(stream)) visitor(value)
    }
}
