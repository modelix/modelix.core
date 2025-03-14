package org.modelix.model.persistent

import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.single.flatMapObservable
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference

/**
 * Serializable object that can be stored in a key value store
 */
interface IKVValue {
    var isWritten: Boolean
    fun serialize(): String
    val hash: String
    fun getDeserializer(): (String) -> IKVValue
    fun getReferencedEntries(): List<KVEntryReference<IKVValue>>
    fun objectDiff(oldObject: IKVValue?, store: IAsyncObjectStore): Observable<IKVValue> {
        return if (oldObject?.hash == hash) observableOfEmpty() else getAllObjects(store)
    }
}

fun IKVValue.getAllObjects(store: IAsyncObjectStore): Observable<IKVValue> {
    val descendants = getReferencedEntries()
        .asObservable()
        .flatMap {
            it.getValue(store).flatMapObservable { it.getAllObjects(store) }
        }
    return observableOf(this).concatWith(descendants)
}
