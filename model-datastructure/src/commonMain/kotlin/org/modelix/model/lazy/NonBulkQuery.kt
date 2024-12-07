package org.modelix.model.lazy

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.maybeOf
import org.modelix.model.persistent.IKVValue

@Deprecated("use IAsyncStore")
class NonBulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery {

    override fun offerPrefetch(key: IPrefetchGoal) {
        // Since no real bulk queries are executed, prefetching doesn't provide any benefit.
    }

    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): Maybe<T> {
        return maybeOf(hash.getValue(store))
    }

    override fun executeQuery() {
        // all requests are processed immediately
    }
}
