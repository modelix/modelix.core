package org.modelix.model.client2

import org.modelix.incremental.SLRUMap
import org.modelix.kotlin.utils.WeakValueMap
import org.modelix.kotlin.utils.getOrPut
import org.modelix.kotlin.utils.runBlockingIfJvm
import org.modelix.model.lazy.MissingEntryException
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectDeserializer
import org.modelix.model.objects.IObjectGraph
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectHash
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.ObjectReferenceImpl
import org.modelix.model.objects.getHashString
import org.modelix.model.objects.upcast
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.BulkRequestStreamExecutor
import org.modelix.streams.IBulkExecutor
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import kotlin.jvm.Synchronized

/**
 * Versions received via [IModelClientV2.pull] are fully loaded. History is loaded on demand.
 */
class ModelClientGraph(
    val client: IModelClientV2Internal,
    val repositoryId: RepositoryId,
) : IObjectGraph {
    private val dataDedupeCache = WeakValueMap<ObjectHash, IObjectData>()

    private var eagerLoadingObjects: Map<ObjectHash, String>? = null
    private val streamExecutor = BulkRequestStreamExecutor(object : IBulkExecutor<String, String> {
        override fun execute(keys: List<String>): Map<String, String> {
            return runBlockingIfJvm {
                client.getObjects(repositoryId, keys.asSequence())
            }
        }
        override suspend fun executeSuspending(keys: List<String>): Map<String, String> {
            return client.getObjects(repositoryId, keys.asSequence())
        }
    })

    /**
     * Lazy loading shouldn't be used extensively without stream based bulk requests, but in case it is, let's have a
     * tiny cache to avoid turning a bad performance into a terrible one.
     */
    private var lazyLoadingCache = object : SLRUMap<ObjectHash, String>(1000, 1000) {
        override fun evicted(key: ObjectHash, value: String) {}
    }

    @Synchronized
    fun loadVersion(
        rootHash: ObjectHash,
        receivedObjects: Map<ObjectHash, String>,
    ): Object<CPVersion> {
        check(eagerLoadingObjects == null) { "Object loading already in progress" }
        eagerLoadingObjects = receivedObjects
        try {
            val loadedVersion = (
                eagerLoadObjects(ObjectReferenceImpl(this, rootHash, CPVersion))
                    ?: throw MissingEntryException(rootHash.toString())
                )
            return withUnloadedHistory(loadedVersion)
        } finally {
            eagerLoadingObjects = null
        }
    }

    fun withUnloadedHistory(version: Object<CPVersion>): Object<CPVersion> {
        val withoutHistory = version.data.withUnloadedHistory()
        return Object(withoutHistory, ObjectReferenceImpl(this, version.getHash(), withoutHistory))
    }

    private fun <T : IObjectData> eagerLoadObjects(
        ref: ObjectReference<T>,
    ): Object<T>? {
        ref.getObjectIfLoaded()?.let { return it }
        val serialized = eagerLoadingObjects?.get(ref.getHash()) ?: return null
        val deserialized: T = dataDedupeCache.getOrPut(ref.getHash()) {
            ref.getDeserializer().deserialize(serialized, this)
        }.upcast()
        ref.upcast<T>().load(deserialized)

        // recursively load other objects
        for (otherRef in deserialized.getAllReferences()) {
            eagerLoadObjects(otherRef)
        }

        return Object(deserialized, ref)
    }

    @Synchronized
    override fun <T : IObjectData> fromHash(
        hash: ObjectHash,
        deserializer: IObjectDeserializer<T>,
    ): ObjectReference<T> {
        return if (deserializer == CPVersion && eagerLoadingObjects?.contains(hash) != true) {
            // References to versions always start as unloaded to allow garbage collection of old versions.
            // Except when they were included in the last response from the server. We only want to get rid of old
            // objects that are already in memory, but new ones were sent for a reason.
            ObjectReferenceImpl(this, hash, deserializer)
        } else {
            dataDedupeCache.get(hash)?.let { ObjectReferenceImpl(this, hash, it.upcast<T>()) }
                ?: ObjectReferenceImpl(this, hash, deserializer)
        }
    }

    @Synchronized
    override fun <T : IObjectData> fromDeserialized(
        hash: ObjectHash,
        data: T,
    ): ObjectReference<T> {
        // Should never be called, because all deserialization happens internally.
        throw UnsupportedOperationException()
    }

    @Synchronized
    override fun <T : IObjectData> fromCreated(data: T): ObjectReference<T> {
        // Caching happens later in [write].
        // This reference state exists to avoid hash computations so we shouldn't add it to the cache yet.
        return ObjectReferenceImpl(this, data)
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return streamExecutor
    }

    private fun <T : IObjectData> findData(ref: ObjectReference<T>): T? {
        return ref.getLoadedData()
            ?: dataDedupeCache.get(ref.getHash())?.upcast<T>()?.also { ref.upcast<T>().load(it) }
            ?: eagerLoadingObjects?.get(ref.getHash())?.let {
                loadFromSerialized(ref, it).data
            }?.upcast<T>()
            ?: lazyLoadingCache[ref.getHash()]?.let { loadFromSerialized(ref, it).data }
    }

    private fun <T : IObjectData> loadFromSerialized(ref: ObjectReference<T>, serialized: String): Object<T> {
        ref.getObjectIfLoaded()?.let { return it }
        val data = deserialize(serialized, ref.getHash(), ref.getDeserializer())
        ref.upcast<T>().load(data)
        return Object(data, ref)
    }

    private fun <T : IObjectData> deserialize(
        serialized: String,
        hash: ObjectHash,
        deserializer: IObjectDeserializer<T>,
    ): T {
        return dataDedupeCache.getOrPut(hash) { deserializer.deserialize(serialized, this) }.upcast()
    }

    @Synchronized
    override fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T> {
        return findData(ref)?.let { IStream.of(it) }
            ?: streamExecutor.enqueue(ref.getHashString()).orNull().map { serialized ->
                if (serialized == null) throw MissingEntryException(ref.getHashString())
                lazyLoadingCache[ref.getHash()] = serialized
                loadFromSerialized(ref, serialized).data
            }
    }

    override fun <T : IObjectData> requestNow(ref: ObjectReference<T>): Object<T> {
        ref.getLoadedData()?.let { return Object(it, ref) }
        return Object(streamExecutor.query { request(ref) }, ref)
    }

    /**
     * Objects are expected to be stored in [IModelClientV2.push] using [Object.objectDiff]
     */
    override fun write(obj: Object<*>) {
        dataDedupeCache.put(obj.getHash(), obj.data)
    }
}
