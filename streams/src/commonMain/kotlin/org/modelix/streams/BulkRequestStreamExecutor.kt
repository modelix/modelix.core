package org.modelix.streams

import org.modelix.streams.engine.Execution
import org.modelix.streams.engine.Step
import org.modelix.streams.engine.fetchStep

/** Default maximum number of keys requested in a single bulk call. */
const val DEFAULT_BULK_REQUEST_BATCH_SIZE: Int = 5000

interface IBulkExecutor<K, V> {
    fun execute(keys: List<K>): Map<K, V>
    suspend fun executeSuspending(keys: List<K>): Map<K, V>

    /**
     * Maximum number of keys to request in a single [execute]/[executeSuspending] call. Each batch round chunks this
     * source's pending keys to this size. The batch size is a property of the data source itself, so it applies
     * regardless of which executor drives the stream.
     */
    val batchSize: Int get() = DEFAULT_BULK_REQUEST_BATCH_SIZE
}

/**
 * Executor that batches the individual fetches enqueued via [enqueue] into bulk calls against the wrapped source.
 *
 * Since the [Step] engine batches structurally (per source per round) and the batch size now lives on the
 * [IBulkExecutor] itself, this type is no longer required for batching to work — any executor (e.g.
 * [SimpleStreamExecutor]) drives the same fetch leaves with the same batching. It is retained for API compatibility
 * and as the holder of [enqueue].
 */
class BulkRequestStreamExecutor<K, V>(
    bulkExecutor: IBulkExecutor<K, V>,
    val batchSize: Int = DEFAULT_BULK_REQUEST_BATCH_SIZE,
) : IStreamExecutor, IStreamExecutorProvider {

    // Honor the batch size passed to this constructor by exposing it on the source the fetch leaves are bound to.
    private val source: IBulkExecutor<K, V> =
        if (bulkExecutor.batchSize == batchSize) {
            bulkExecutor
        } else {
            object : IBulkExecutor<K, V> by bulkExecutor {
                override val batchSize: Int get() = this@BulkRequestStreamExecutor.batchSize
            }
        }

    override fun getStreamExecutor(): IStreamExecutor = this

    @Suppress("UNCHECKED_CAST")
    fun enqueue(key: K): IStream.ZeroOrOne<V> =
        StreamImpl<V> { execution -> fetchStep(execution, source as IBulkExecutor<Any?, Any?>, key) as Step<V> }

    override fun <T> query(body: () -> IStream.One<T>): T {
        return IStreamExecutor.CONTEXT.computeWith(this) {
            val execution = Execution()
            execution.drive(body().asStep(execution)).single()
        }
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        return IStreamExecutor.CONTEXT.runInCoroutine(this) {
            val execution = Execution()
            execution.driveSuspending(body().asStep(execution)).single()
        }
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        IStreamExecutor.CONTEXT.computeWith(this) {
            val execution = Execution()
            execution.drive(streamProvider().asStep(execution)).forEach(visitor)
        }
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        IStreamExecutor.CONTEXT.runInCoroutine(this) {
            val execution = Execution()
            execution.driveSuspending(streamProvider().asStep(execution)).forEach { visitor(it) }
        }
    }
}
