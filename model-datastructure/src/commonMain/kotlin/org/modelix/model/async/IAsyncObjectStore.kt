package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.MissingEntryException
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.IObjectWriter
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectHash
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.getHashString
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.plus

interface IAsyncObjectStore : IStreamExecutorProvider {
    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyKeyValueStore(): IKeyValueStore

    @Deprecated("Use IAsyncObjectStore")
    fun getLegacyObjectStore(): IDeserializingKeyValueStore

    fun <T : Any> getIfCached(key: ObjectRequest<T>): T?
    fun <T : Any> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T>

    fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, Any?>>
    fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, Any?>>
    fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero

    fun clearCache()
}

fun IAsyncObjectStore.asObjectLoader(): IObjectLoader = AsyncStoreAsObjectLoader(this)
fun IAsyncObjectStore.asObjectWriter(): IObjectWriter = AsyncStoreAsObjectWriter(this)

class AsyncStoreAsObjectLoader(val store: IAsyncObjectStore) : IObjectLoader {
    override fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T> {
        return store.get(ObjectRequest(ref.getHashString(), ref.getDeserializer()))
            .exceptionIfEmpty { MissingEntryException(ref.getHashString()) }
    }
}

class AsyncStoreAsObjectWriter(val store: IAsyncObjectStore) : IObjectWriter {
    override fun write(hash: ObjectHash, obj: IObjectData) {
        store.getStreamExecutor().execute {
            store.putAll(mapOf(ObjectRequest(hash.toString(), obj.getDeserializer()) to obj))
        }
    }
}

// TODO rename to resolveData
fun <T : IObjectData> ObjectReference<T>.getObject(store: IAsyncObjectStore): Object<T> {
    return store.getStreamExecutor().query { resolve(store.asObjectLoader()) }
}

class ObjectRequest<E : Any>(val hash: String, val deserializer: (String) -> E) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ObjectRequest<*>

        return hash == other.hash
    }

    override fun hashCode(): Int {
        return hash.hashCode()
    }
}

fun <T : IObjectData> ObjectReference<T>.toObjectRequest(): ObjectRequest<T> {
    return ObjectRequest(this.getHashString(), this.getDeserializer())
}

@Deprecated("Use IObject.getDescendantsAndSelf")
fun IAsyncObjectStore.getRecursively(
    key: ObjectReference<IObjectData>,
    seenHashes: MutableSet<ObjectHash> = HashSet(),
): IStream.Many<Pair<ObjectReference<*>, IObjectData>> {
    return if (seenHashes.contains(key.getHash())) {
        IStream.empty()
    } else {
        seenHashes.add(key.getHash())
        get(key.toObjectRequest()).flatMap {
            IStream.of(key to it).plus(IStream.many(it.getAllReferences()).flatMap { getRecursively(it, seenHashes) })
        }
    }
}
