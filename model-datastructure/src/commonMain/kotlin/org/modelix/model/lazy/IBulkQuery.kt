package org.modelix.model.lazy

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

@Deprecated("use IAsyncStore")
interface IBulkQuery : IStreamExecutorProvider {
    fun executeQuery()
    fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IStream.Many<O>): IStream.Many<O> = IStream.many(input).flatMap { f(it) }
    fun <T> constant(value: T): IStream.One<T> = IStream.of(value)
    fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T>

    companion object {
        val CONTEXT_QUERY = ContextValue<IBulkQuery>()
    }
}

open class BulkQueryConfiguration {
    /**
     * The maximum number of objects that is requested in one request.
     */
    var requestBatchSize: Int = defaultRequestBatchSize

    companion object {
        var defaultRequestBatchSize: Int = 5_000
    }
}
