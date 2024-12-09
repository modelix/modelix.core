package org.modelix.model.lazy

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.singleOf
import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.persistent.IKVValue

@Deprecated("use IAsyncStore")
interface IBulkQuery {
    @Deprecated("Prefetching will be replaced by usages of IAsyncNode")
    fun offerPrefetch(key: IPrefetchGoal)
    fun executeQuery()
    fun <I, O> flatMap(input: Iterable<I>, f: (I) -> Observable<O>): Observable<O> = input.asObservable().flatMap { f(it) }
    fun <T> constant(value: T): Single<T> = singleOf(value)
    fun <T : IKVValue> query(hash: IKVEntryReference<T>): Maybe<T>

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
