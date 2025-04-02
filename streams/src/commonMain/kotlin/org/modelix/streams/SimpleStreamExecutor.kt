package org.modelix.streams

import kotlinx.coroutines.flow.single
import org.modelix.kotlin.utils.runBlockingIfJvm

object SimpleStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        return SequenceStreamBuilder.INSTANCE.convert(body()).single()
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        return FlowStreamBuilder.INSTANCE.convert(body()).single()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        SequenceStreamBuilder.INSTANCE.convert(streamProvider()).forEach(visitor)
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        FlowStreamBuilder.INSTANCE.convert(streamProvider()).collect(visitor)
    }
}

object BlockingStreamExecutor : IStreamExecutor {
    override fun <T> query(body: () -> IStream.One<T>): T {
        return runBlockingIfJvm {
            FlowStreamBuilder.INSTANCE.convert(body()).single()
        }
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        return FlowStreamBuilder.INSTANCE.convert(body()).single()
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        runBlockingIfJvm {
            FlowStreamBuilder.INSTANCE.convert(streamProvider()).collect { visitor(it) }
        }
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        FlowStreamBuilder.INSTANCE.convert(streamProvider()).collect(visitor)
    }
}
