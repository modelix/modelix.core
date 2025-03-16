package org.modelix.model.lazy

import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import kotlin.jvm.Synchronized

@Deprecated("use IAsyncStore")
class SynchronizedBulkQuery(val nonThreadSafeQuery: IBulkQuery) : IBulkQuery, IStreamExecutorProvider by nonThreadSafeQuery {
    @Synchronized
    override fun executeQuery() {
        return nonThreadSafeQuery.executeQuery()
    }

    @Synchronized
    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): IStream.ZeroOrOne<T> {
        return nonThreadSafeQuery.query(hash)
    }
}

fun IBulkQuery.asSynchronized(): IBulkQuery {
    return if (this is SynchronizedBulkQuery) this else SynchronizedBulkQuery(this)
}
