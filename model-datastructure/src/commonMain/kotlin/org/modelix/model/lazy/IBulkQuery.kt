package org.modelix.model.lazy

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

@Deprecated("use IAsyncStore")
interface IBulkQuery {
    fun executeQuery()
    fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IStream.Many<O>): IStream.Many<O> = IStream.many(input).flatMap { f(it) }
    fun <T> constant(value: T): IStream.One<T> = IStream.of(value)
    fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T>

    companion object {
        val CONTEXT_QUERY = ContextValue<IBulkQuery>()
    }
}

@Deprecated("use IAsyncStore")
open class BulkQueryConfiguration {
    /**
     * The maximum number of objects that is requested in one request.
     */
    var requestBatchSize: Int = defaultRequestBatchSize

    /**
     * If a request contains fewer objects than [prefetchBatchSize], it is filled up with additional objects that are
     * predicted to be required in the future.
     */
    var prefetchBatchSize: Int? = defaultPrefetchBatchSize

    companion object {
        var defaultRequestBatchSize: Int = 5_000
        var defaultPrefetchBatchSize: Int? = null
    }
}
