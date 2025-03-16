package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.plus

interface IAsyncObjectStore : IStreamExecutorProvider {
    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyKeyValueStore(): IKeyValueStore

    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyObjectStore(): IDeserializingKeyValueStore

    fun <T : Any> getIfCached(key: ObjectHash<T>): T?
    fun <T : Any> get(key: ObjectHash<T>): IStream.ZeroOrOne<T>

    fun getAllAsStream(keys: IStream.Many<ObjectHash<*>>): IStream.Many<Pair<ObjectHash<*>, Any?>>
    fun getAllAsMap(keys: List<ObjectHash<*>>): IStream.One<Map<ObjectHash<*>, Any?>>
    fun putAll(entries: Map<ObjectHash<*>, IKVValue>): IStream.Zero
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

fun IAsyncObjectStore.getRecursively(key: IKVEntryReference<IKVValue>, seenHashes: MutableSet<String> = HashSet()): IStream.Many<Pair<IKVEntryReference<*>, IKVValue>> {
    return if (seenHashes.contains(key.getHash())) {
        IStream.empty()
    } else {
        seenHashes.add(key.getHash())
        get(key.toObjectHash()).flatMap {
            IStream.of(key to it).plus(IStream.many(it.getReferencedEntries()).flatMap { getRecursively(it, seenHashes) })
        }
    }
}
