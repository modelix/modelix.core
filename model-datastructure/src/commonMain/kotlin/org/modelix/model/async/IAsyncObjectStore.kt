package org.modelix.model.async

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asObservable
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.single.Single
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

interface IAsyncObjectStore {
    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyKeyValueStore(): IKeyValueStore

    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyObjectStore(): IDeserializingKeyValueStore

    fun <T : Any> getIfCached(key: ObjectHash<T>): T?
    fun <T : Any> get(key: ObjectHash<T>): Maybe<T>

    fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>>
    fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>>
    fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable
}

class ObjectHash<E : Any>(val hash: String, val deserializer: (String) -> E) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ObjectHash<*>

        return hash == other.hash
    }

    override fun hashCode(): Int {
        return hash.hashCode()
    }
}

fun <T : IKVValue> IKVEntryReference<T>.toObjectHash(): ObjectHash<T> {
    require(this.isWritten()) { "use IKVEntryReference.getValue instead" }
    return ObjectHash(this.getHash(), this.getDeserializer())
}

fun <T : IKVValue> ObjectHash<*>.toKVEntryReference(): IKVEntryReference<T> = KVEntryReference(hash, deserializer as ((String) -> T))

fun IAsyncObjectStore.getRecursively(key: IKVEntryReference<IKVValue>, seenHashes: MutableSet<String> = HashSet()): Observable<Pair<IKVEntryReference<*>, IKVValue>> {
    return if (seenHashes.contains(key.getHash())) {
        observableOfEmpty()
    } else {
        seenHashes.add(key.getHash())
        get(key.toObjectHash()).asObservable().flatMap {
            observableOf(key to it).concatWith(it.getReferencedEntries().asObservable().flatMap { getRecursively(it, seenHashes) })
        }
    }
}
