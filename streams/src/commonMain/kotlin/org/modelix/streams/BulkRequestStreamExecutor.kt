package org.modelix.streams

import org.modelix.streams.engine.Execution
import org.modelix.streams.engine.Step
import org.modelix.streams.engine.drive
import org.modelix.streams.engine.driveSuspending
import org.modelix.streams.engine.fetchStep

interface IBulkExecutor<K, V> {
    fun execute(keys: List<K>): Map<K, V>
    suspend fun executeSuspending(keys: List<K>): Map<K, V>
}

/**
 * Executor that batches the individual fetches enqueued via [enqueue] into bulk calls against [bulkExecutor].
 *
 * Batching is structural: [enqueue] returns a stream backed by a fetch leaf bound to [bulkExecutor], and the round
 * driver groups all fetches reachable in a round (across independent stream branches) into a single
 * [IBulkExecutor.execute] call, chunked to [batchSize]. Dependent fetches (reached through `flatMap`) fall into later
 * rounds. This replaces the previous Reaktive subscribe-collect-batch mechanism.
 */
class BulkRequestStreamExecutor<K, V>(
    private val bulkExecutor: IBulkExecutor<K, V>,
    val batchSize: Int = 5000,
) : IStreamExecutor, IStreamExecutorProvider {

    override fun getStreamExecutor(): IStreamExecutor = this

    @Suppress("UNCHECKED_CAST")
    fun enqueue(key: K): IStream.ZeroOrOne<V> =
        StreamImpl<V> { execution -> fetchStep(execution, bulkExecutor as IBulkExecutor<Any?, Any?>, key) as Step<V> }

    override fun <T> query(body: () -> IStream.One<T>): T {
        return IStreamExecutor.CONTEXT.computeWith(this) {
            val execution = Execution()
            execution.drive(body().asStep(execution), batchSize).single()
        }
    }

    override suspend fun <T> querySuspending(body: suspend () -> IStream.One<T>): T {
        return IStreamExecutor.CONTEXT.runInCoroutine(this) {
            val execution = Execution()
            execution.driveSuspending(body().asStep(execution), batchSize).single()
        }
    }

    override fun <T> iterate(streamProvider: () -> IStream.Many<T>, visitor: (T) -> Unit) {
        IStreamExecutor.CONTEXT.computeWith(this) {
            val execution = Execution()
            execution.drive(streamProvider().asStep(execution), batchSize).forEach(visitor)
        }
    }

    override suspend fun <T> iterateSuspending(
        streamProvider: suspend () -> IStream.Many<T>,
        visitor: suspend (T) -> Unit,
    ) {
        IStreamExecutor.CONTEXT.runInCoroutine(this) {
            val execution = Execution()
            execution.driveSuspending(streamProvider().asStep(execution), batchSize).forEach { visitor(it) }
        }
    }
}
