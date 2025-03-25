package org.modelix.model.async

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.ObjectReferenceImpl
import org.modelix.datastructures.objects.getHashString
import org.modelix.model.lazy.MissingEntryException
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

data class LazyLoadingObjectGraph(val store: IAsyncObjectStore) : IObjectGraph {
    override fun <T : IObjectData> fromHash(
        hash: ObjectHash,
        deserializer: IObjectDeserializer<T>,
    ): ObjectReference<T> {
        return ObjectReferenceImpl(this, hash, deserializer)
    }

    override fun <T : IObjectData> fromDeserialized(
        hash: ObjectHash,
        data: T,
    ): ObjectReference<T> {
        return ObjectReferenceImpl(this, hash, data)
    }

    override fun <T : IObjectData> fromCreated(data: T): ObjectReference<T> {
        return ObjectReferenceImpl(this, data)
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return store.getStreamExecutor()
    }

    override fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T> {
        ref.getLoadedData()?.let { return IStream.of(it) }
        return store.get(ObjectRequest(ref.getHashString(), ref.getDeserializer(), this))
            .exceptionIfEmpty { MissingEntryException(ref.getHashString()) }
    }

    override fun <T : IObjectData> requestNow(ref: ObjectReference<T>): Object<T> {
        ref.getLoadedData()?.let { return Object(it, ref) }
        return store.getStreamExecutor().query { request(ref) }.let { Object(it, ref) }
    }

    override fun write(obj: Object<*>) {
        getStreamExecutor().query {
            store.putAll(mapOf(ObjectRequest(obj.getHashString(), obj.ref.getDeserializer(), this) to obj.data)).andThenUnit()
        }
    }
}
