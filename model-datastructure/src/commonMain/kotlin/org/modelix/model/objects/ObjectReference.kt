package org.modelix.model.objects

import org.modelix.model.persistent.HashUtil
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * The purpose of this interface is to have the `out` variance on the `E` parameter,
 * which wouldn't work on the implementation class.
 */
interface ObjectReference<out E : IObjectData> {
    fun getHash(): ObjectHash
    fun getDeserializer(): (String) -> E
    fun deref(): E?
    fun unload()
    fun load(serialized: String)
    fun requestData(loader: IObjectLoader): IStream.One<E>
    fun resolve(loader: IObjectLoader): IStream.One<Object<E>>
    fun write(writer: IObjectWriter)
    fun diff(oldRef: ObjectReference<IObjectData>?, loader: IObjectLoader): IStream.Many<Object<IObjectData>>

    companion object {
        @JvmName("fromHashString")
        operator fun <T : IObjectData> invoke(hash: String, deserializer: (String) -> T): ObjectReference<T> =
            ObjectReferenceImpl(ObjectHash(hash), deserializer)

        @JvmName("fromHash")
        operator fun <T : IObjectData> invoke(hash: ObjectHash, deserializer: (String) -> T): ObjectReference<T> =
            ObjectReferenceImpl(hash, deserializer)

        @JvmName("fromDeserialized")
        operator fun <T : IObjectData> invoke(hash: ObjectHash, data: T): ObjectReference<T> =
            ObjectReferenceImpl(hash, data)

        @JvmName("fromData")
        operator fun <T : IObjectData> invoke(data: T): ObjectReference<T> =
            ObjectReferenceImpl(data)
    }
}

fun ObjectReference<*>.getHashString(): String = getHash().toString()

/**
 * Replicated data structures in Modelix are stored as hash trees.
 * Nodes of the tree are connected by instances of [ObjectReferenceImpl], which allows the tree to be partially loaded.
 *
 * An [IObjectLoader] can control the memory and performance properties of the tree. It can keep the whole tree
 * in memory or load nodes on each request (with different levels of caching).
 */
private class ObjectReferenceImpl<E : IObjectData> private constructor(private var obj: Obj<E>) : ObjectReference<E> {
    constructor(hash: ObjectHash, deserializer: (String) -> E) : this(Unloaded(hash, deserializer))
    constructor(hash: ObjectHash, obj: E) : this(Loaded(hash, obj))
    constructor(obj: E) : this(Created(obj))

    override fun equals(other: Any?): Boolean {
        error("Use .getHash() for comparing references")
    }

    override fun hashCode(): Int {
        error("Use .getHash() for comparing references")
    }

    override fun getHash(): ObjectHash {
        return obj.hash
    }

    override fun getDeserializer(): (String) -> E {
        return obj.deserializer
    }

    override fun deref(): E? {
        return obj.deref()
    }

    override fun unload() {
        obj.unload(this)
    }

    override fun load(serialized: String) {
        obj.load(this, serialized)
    }

    override fun requestData(loader: IObjectLoader): IStream.One<E> {
        return obj.request(this, loader)
    }

    override fun resolve(loader: IObjectLoader): IStream.One<Object<E>> {
        return requestData(loader).map { Object(it, this) }
    }

    override fun write(writer: IObjectWriter) {
        obj.write(this, writer)
    }

    override fun diff(oldRef: ObjectReference<IObjectData>?, loader: IObjectLoader): IStream.Many<Object<IObjectData>> {
        return if (oldRef == null) {
            resolve(loader).flatMap { it.getDescendantsAndSelf(loader) }
        } else if (getHash() == oldRef.getHash()) {
            IStream.empty()
        } else {
            resolve(loader).zipWith(oldRef.resolve(loader)) { newObj, oldObj ->
                newObj.objectDiff(oldObj, loader)
            }.flatten()
        }
    }

    private abstract class Obj<E : IObjectData> {
        abstract val hash: ObjectHash
        abstract val deserializer: (String) -> E
        abstract fun unload(ref: ObjectReferenceImpl<E>)
        abstract fun load(ref: ObjectReferenceImpl<E>, serialized: String)
        abstract fun deref(): E?
        abstract fun request(ref: ObjectReferenceImpl<E>, loader: IObjectLoader): IStream.One<E>
        abstract fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter)
    }

    private abstract class Written<E : IObjectData>(override val hash: ObjectHash) : Obj<E>() {
        override fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter) {}
    }

    private class Created<E : IObjectData>(val value: E) : Obj<E>() {
        override val hash by lazy(LazyThreadSafetyMode.PUBLICATION) { ObjectHash(HashUtil.sha256(value.serialize())) }
        override val deserializer: (String) -> E
            get() = value.getDeserializer() as (String) -> E

        override fun unload(ref: ObjectReferenceImpl<E>) {
            throw IllegalStateException("Object isn't persisted yet and would get lost: $value")
        }
        override fun load(ref: ObjectReferenceImpl<E>, serialized: String) {}

        override fun deref(): E = value
        override fun request(ref: ObjectReferenceImpl<E>, loader: IObjectLoader): IStream.One<E> = IStream.of(value)
        override fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter) {
            writer.write(hash, value)
            for (otherReference in value.getAllReferences()) {
                otherReference.write(writer)
            }
            ref.obj = Loaded(hash, value)
        }
    }

    private class Loaded<E : IObjectData>(hash: ObjectHash, val value: E) : Written<E>(hash) {
        override val deserializer: (String) -> E
            get() = value.getDeserializer() as (String) -> E
        override fun unload(ref: ObjectReferenceImpl<E>) {
            ref.obj = Unloaded(hash, value.getDeserializer() as (String) -> E)
        }
        override fun load(ref: ObjectReferenceImpl<E>, serialized: String) {}
        override fun deref(): E = value
        override fun request(ref: ObjectReferenceImpl<E>, loader: IObjectLoader): IStream.One<E> = IStream.of(value)
    }

    private class Unloaded<E : IObjectData>(hash: ObjectHash, override val deserializer: (String) -> E) : Written<E>(hash) {
        override fun unload(ref: ObjectReferenceImpl<E>) {}
        override fun load(ref: ObjectReferenceImpl<E>, serialized: String) {
            ref.obj = Loaded(hash, deserializer(serialized))
        }
        override fun deref(): E? = null
        override fun request(ref: ObjectReferenceImpl<E>, loader: IObjectLoader): IStream.One<E> = loader.request(ref)
    }
}

// TODO rename to resolveBoth
fun <T1 : IObjectData, T2 : IObjectData, R> ObjectReference<T1>.requestBoth(
    other: ObjectReference<T2>,
    loader: IObjectLoader,
    mapper: (Object<T1>, Object<T2>) -> R,
): IStream.One<R> {
    return resolve(loader).zipWith(other.resolve(loader), mapper)
}

fun <T1 : IObjectData, T2 : IObjectData> ObjectReference<T1>.customDiff(
    oldObject: ObjectReference<T2>,
    loader: IObjectLoader,
    mapper: (Object<T1>, Object<T2>) -> IStream.Many<Object<*>>,
): IStream.Many<Object<*>> {
    return if (this.getHash() == oldObject.getHash()) {
        IStream.empty()
    } else {
        this.requestBoth(oldObject, loader) { n, o ->
            mapper(n, o)
        }.flatten()
    }
}

@JvmInline
value class ObjectHash(private val hash: String) {
    override fun toString(): String {
        return hash
    }
}

interface IObjectLoader {
    fun <T : IObjectData> request(ref: ObjectReference<T>): IStream.One<T>
}
