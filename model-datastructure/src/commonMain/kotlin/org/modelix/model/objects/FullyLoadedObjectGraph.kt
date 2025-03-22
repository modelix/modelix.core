package org.modelix.model.objects

import org.modelix.kotlin.utils.WeakValueMap
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor
import org.modelix.streams.withSequences
import kotlin.jvm.Synchronized

/**
 * Covers the uses cases of the model client where versions are not lazily loaded, but fetched as a delta from the
 * server. A new version of the model is constructed from a previous one. The server sends only those objects that are
 * have changed compared to the previous version.
 * This class maintains a map of all the objects and reuses them for the new version. The map uses weak references which
 * allows the garbage collector to remove objects of old unused versions.
 */
class FullyLoadedObjectGraph : IObjectGraph, IObjectWriter {

    /**
     * Using a WeakValueMap works well with the delta based pulling of versions. The base version holds
     * references in loaded state to all it's objects which prevents the weak values from being garbage collected.
     * The delta for the new version then needs exactly those entries that are still in this cache for constructing a
     * fully loaded object tree.
     */
    private val dedupeCache = WeakValueMap<ObjectHash, ObjectReference<*>>()

    private var currentReceivedObjects: Map<ObjectHash, String>? = null

    fun getExistingReference(hash: ObjectHash): ObjectReference<*>? {
        return dedupeCache.get(hash)
    }

    @Synchronized
    fun <T : IObjectData> loadObjects(
        rootHash: ObjectHash,
        rootDeserializer: IObjectDeserializer<T>,
        receivedObjects: Map<ObjectHash, String>,
    ): Object<T> {
        check(currentReceivedObjects == null) { "Object loading already in progress" }
        currentReceivedObjects = receivedObjects
        try {
            val ref = loadReference(rootHash, rootDeserializer)
            return Object(ref.getLoadedData()!!, ref)
        } finally {
            currentReceivedObjects = null
        }
    }

    private fun <T : IObjectData> loadReference(
        hash: ObjectHash,
        deserializer: IObjectDeserializer<T>,
    ): ObjectReference<T> {
        val existingRef = dedupeCache.get(hash)?.upcast<T>()
        if (existingRef != null) {
            check(existingRef.isLoaded()) { "Reference is unloaded: $existingRef" }
            return existingRef
        }

        val data = currentReceivedObjects?.get(hash)?.let { deserializer.deserialize(it, this) }
        if (data == null) {
            throw IllegalStateException("Object data not received: $hash, $deserializer")
        }
        return ObjectReferenceImpl(this, hash, data).also { dedupeCache.put(hash, it) }
    }

    /**
     * This will be called after receiving objects from the server. Since there is no lazy loading, all referenced
     * objects are expected to exist.
     */
    @Synchronized
    override fun <T : IObjectData> fromHash(
        hash: ObjectHash,
        deserializer: IObjectDeserializer<T>,
    ): ObjectReference<T> {
        return loadReference(hash, deserializer)
    }

    /**
     * Objects are received in serialized form and are provided by calling [loadObjects]. This method is not expected
     * to be called in this use case.
     */
    @Synchronized
    override fun <T : IObjectData> fromDeserialized(
        hash: ObjectHash,
        data: T,
    ): ObjectReference<T> {
        throw UnsupportedOperationException()
    }

    /**
     * Called while changes are made locally.
     */
    @Synchronized
    override fun <T : IObjectData> fromCreated(data: T): ObjectReference<T> {
        return ObjectReferenceImpl(this, data)
    }

    /**
     * To avoid unnecessary hash computations while a new object tree is still being created, [fromCreated] doesn't
     * put the new objects into the cache. Only when [ObjectReference.write] is called on the root of the object tree,
     * it is considered as 'done' and the new references are indexed for deduplication.
     *
     * The objects are not actually written here, because the model client collects them using [Object.objectDiff] and
     * calls [ObjectReference.write] after the upload. They just need to be indexed before a new version can be
     * constructed from the server's response.
     */
    @Synchronized
    override fun write(obj: Object<*>) {
        require(obj.graph == this) { "Not part of this graph: $obj" }
        dedupeCache.put(obj.getHash(), obj.ref)
//        if (obj.data.getDeserializer().isGarbageCollectionRoot()) {
//            obj.ref.unload()
//        }
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return SimpleStreamExecutor().withSequences()
    }

    override fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T> {
        ref.getLoadedData()?.let { return IStream.of(it) }
        throw IllegalStateException("Object not loaded: ${ref.getHash()}, ${ref.getDeserializer()}")
    }

    override fun <T : IObjectData> requestNow(ref: ObjectReference<T>): Object<T> {
        ref.getLoadedData()?.let { return Object(it, ref) }
        throw IllegalStateException("Object not loaded: ${ref.getHash()}, ${ref.getDeserializer()}")
    }
}
