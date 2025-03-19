package org.modelix.model.objects

import org.modelix.streams.IExecutableStream
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.flatten

/**
 * The purpose of this interface is to have the `out` variance on the `E` parameter,
 * which wouldn't work on the implementation class.
 */
sealed interface ObjectReference<out E : IObjectData> {
    val graph: IObjectGraph
    fun getHash(): ObjectHash
    fun getDeserializer(): IObjectDeserializer<E>
    fun getLoadedData(): E?
    fun getObjectIfLoaded(): Object<E>? = getLoadedData()?.let { Object(it, this) }
    fun isLoaded(): Boolean
    fun unload()
    fun asUnloaded() = graph.fromHash(getHash(), getDeserializer())
    fun resolveData(): IStream.One<E>
    fun resolve(): IStream.One<Object<E>>
    fun resolveLater(): IExecutableStream.One<Object<E>> = graph.getStreamExecutor().queryLater { resolve() }
    fun resolveNow(): Object<E> = graph.getStreamExecutor().query { resolve() }
    fun write(writer: IObjectWriter)
    fun write() = write(graph)
    fun diff(oldRef: ObjectReference<*>?): IStream.Many<Object<IObjectData>>
}

fun ObjectReference<*>.getHashString(): String = getHash().toString()

// TODO rename to resolveBoth
fun <T1 : IObjectData, T2 : IObjectData, R> ObjectReference<T1>.requestBoth(
    other: ObjectReference<T2>,
    mapper: (Object<T1>, Object<T2>) -> R,
): IStream.One<R> {
    return resolve().zipWith(other.resolve(), mapper)
}

fun <T1 : IObjectData, T2 : IObjectData> ObjectReference<T1>.customDiff(
    oldObject: ObjectReference<T2>,
    mapper: (Object<T1>, Object<T2>) -> IStream.Many<Object<*>>,
): IStream.Many<Object<*>> {
    return if (this.getHash() == oldObject.getHash()) {
        IStream.empty()
    } else {
        this.requestBoth(oldObject) { n, o ->
            mapper(n, o)
        }.flatten()
    }
}

interface IObjectLoader : IStreamExecutorProvider {
    override fun getStreamExecutor(): IStreamExecutor
    fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T>
    fun <T : IObjectData> requestNow(ref: ObjectReference<T>): Object<T>
}

fun <T : IObjectData> ObjectReference<*>.upcast(): ObjectReferenceImpl<T> {
    @Suppress("UNCHECKED_CAST")
    return this as ObjectReferenceImpl<T>
}
