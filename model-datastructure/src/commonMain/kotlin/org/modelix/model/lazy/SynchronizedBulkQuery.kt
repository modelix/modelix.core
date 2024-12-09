package org.modelix.model.lazy

import com.badoo.reaktive.maybe.Maybe
import org.modelix.model.persistent.IKVValue
import kotlin.jvm.Synchronized

@Deprecated("use IAsyncStore")
class SynchronizedBulkQuery(val nonThreadSafeQuery: IBulkQuery) : IBulkQuery {
    @Synchronized
    override fun offerPrefetch(key: IPrefetchGoal) {
        return nonThreadSafeQuery.offerPrefetch(key)
    }

    @Synchronized
    override fun executeQuery() {
        return nonThreadSafeQuery.executeQuery()
    }

    @Synchronized
    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): Maybe<T> {
        return nonThreadSafeQuery.query(hash)
    }
}

fun IBulkQuery.asSynchronized(): IBulkQuery {
    return if (this is SynchronizedBulkQuery) this else SynchronizedBulkQuery(this)
}
