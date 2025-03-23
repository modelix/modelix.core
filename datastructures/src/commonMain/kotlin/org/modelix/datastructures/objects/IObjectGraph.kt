package org.modelix.datastructures.objects

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor
import org.modelix.streams.withSequences
import kotlin.jvm.JvmName

interface IObjectGraph : IObjectReferenceFactory, IObjectLoader, IObjectWriter {
    companion object {
        /**
         * Should only be used for temporary local objects that are not replicated and not part of any shared state.
         */
        @DelicateModelixApi
        val FREE_FLOATING: IObjectGraph = FreeFloatingObjectGraph()
    }
}

@JvmName("getObjectFromHashString")
fun <T : IObjectData> IObjectGraph.getObject(hash: String, deserializer: IObjectDeserializer<T>): Object<T> {
    return getObject(ObjectHash(hash), deserializer)
}

fun <T : IObjectData> IObjectGraph.getObject(hash: ObjectHash, deserializer: IObjectDeserializer<T>): Object<T> {
    return fromHash(hash, deserializer).resolveLater().query()
}

private class FreeFloatingObjectGraph() : IObjectGraph {
    override fun <T : IObjectData> fromHash(hash: ObjectHash, deserializer: IObjectDeserializer<T>): ObjectReference<T> =
        ObjectReferenceImpl(this, hash, deserializer)

    override fun <T : IObjectData> fromDeserialized(hash: ObjectHash, data: T): ObjectReference<T> =
        ObjectReferenceImpl(this, hash, data)

    override fun <T : IObjectData> fromCreated(data: T): ObjectReference<T> =
        ObjectReferenceImpl(this, data)

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

    override fun write(obj: Object<*>) {
        throw UnsupportedOperationException("Free floating objects are not expected to be part of the final graph.")
    }
}
