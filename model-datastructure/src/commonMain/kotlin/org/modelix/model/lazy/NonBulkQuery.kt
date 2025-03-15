package org.modelix.model.lazy

import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

@Deprecated("use IAsyncStore")
class NonBulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery {

    override fun offerPrefetch(key: IPrefetchGoal) {
        // Since no real bulk queries are executed, prefetching doesn't provide any benefit.
    }

    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T> {
        return IStream.of(hash.getValue(store))
    }

    override fun executeQuery() {
        // all requests are processed immediately
    }
}
