package org.modelix.model.lazy

import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

@Deprecated("use IAsyncStore")
class NonBulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery, IStreamExecutorProvider by store {

    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T> {
        return IStream.of(hash.getValue(store))
    }

    override fun executeQuery() {
        // all requests are processed immediately
    }
}
