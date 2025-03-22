package org.modelix.model.async

import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectDeserializer
import org.modelix.model.objects.IObjectGraph
import org.modelix.model.objects.IObjectReferenceFactory
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.plus

interface IAsyncObjectStore : IStreamExecutorProvider {
    fun getLegacyKeyValueStore(): IKeyValueStore
    fun getLegacyObjectStore(): IDeserializingKeyValueStore

    fun <T : IObjectData> getIfCached(key: ObjectRequest<T>): T?
    fun <T : IObjectData> get(key: ObjectRequest<T>): IStream.ZeroOrOne<T>

    fun getAllAsStream(keys: IStream.Many<ObjectRequest<*>>): IStream.Many<Pair<ObjectRequest<*>, IObjectData?>>
    fun getAllAsMap(keys: List<ObjectRequest<*>>): IStream.One<Map<ObjectRequest<*>, IObjectData?>>
    fun putAll(entries: Map<ObjectRequest<*>, IObjectData>): IStream.Zero

    fun clearCache()

    fun asObjectGraph(): IObjectGraph = LazyLoadingObjectGraph(this)
}

fun IObjectGraph.getAsyncStore(): IAsyncObjectStore {
    return when (this) {
        is LazyLoadingObjectGraph -> this.store
        else -> throw RuntimeException("Unknown graph type: $this")
    }
}

class ObjectRequest<E : IObjectData>(
    val hash: String,
    val deserializer: IObjectDeserializer<E>,
    val referenceFactory: IObjectReferenceFactory,
) {
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
