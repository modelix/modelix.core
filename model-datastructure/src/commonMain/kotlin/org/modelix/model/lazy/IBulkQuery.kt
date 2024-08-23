/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
