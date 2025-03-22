package org.modelix.model.objects

import org.modelix.streams.IStream
import org.modelix.streams.flatten

/**
 * Replicated data structures in Modelix are stored as hash trees.
 * Nodes of the tree are connected by instances of [ObjectReferenceImpl], which allows the tree to be partially loaded.
 *
 * An [IObjectLoader] can control the memory and performance properties of the tree. It can keep the whole tree
 * in memory or load nodes on each request (with different levels of caching).
 */
class ObjectReferenceImpl<E : IObjectData> private constructor(
    override val graph: IObjectGraph,
    private var state: State<E>,
) : ObjectReference<E> {
    constructor(graph: IObjectGraph, hash: ObjectHash, deserializer: IObjectDeserializer<E>) :
        this(graph, Unloaded(hash, deserializer))
    constructor(graph: IObjectGraph, hash: ObjectHash, obj: E) :
        this(graph, Loaded(hash, obj))
    constructor(graph: IObjectGraph, obj: E) :
        this(graph, Created(obj))

    override fun getHash(): ObjectHash = state.hash
    override fun getDeserializer(): IObjectDeserializer<E> = state.deserializer
    override fun getLoadedData(): E? = state.getLoadedData()
    override fun unload() = state.unload(this)
    fun load(data: E) = state.load(this, data)
    override fun isLoaded(): Boolean = state.isLoaded()
    override fun resolveData(): IStream.One<E> = state.request(this)
    override fun resolve(): IStream.One<Object<E>> = resolveData().map { Object(it, this) }
    override fun write(writer: IObjectWriter) = state.write(this, writer)

    override fun diff(oldRef: ObjectReference<IObjectData>?): IStream.Many<Object<IObjectData>> {
        return if (oldRef == null) {
            resolve().flatMap { it.getDescendantsAndSelf() }
        } else if (getHash() == oldRef.getHash()) {
            IStream.Companion.empty()
        } else {
            resolve().zipWith(oldRef.resolve()) { newObj, oldObj ->
                newObj.objectDiff(oldObj)
            }.flatten()
        }
    }

    override fun equals(other: Any?): Boolean {
        error("Use .getHash() for comparing references")
    }

    override fun hashCode(): Int {
        error("Use .getHash() for comparing references")
    }

    override fun toString(): String {
        return state.toString()
    }

    private abstract class State<E : IObjectData> {
        abstract val hash: ObjectHash
        abstract val deserializer: IObjectDeserializer<E>
        abstract fun unload(ref: ObjectReferenceImpl<E>)
        abstract fun load(ref: ObjectReferenceImpl<E>, data: E)
        abstract fun isLoaded(): Boolean
        abstract fun getLoadedData(): E?
        abstract fun request(ref: ObjectReferenceImpl<E>): IStream.One<E>
        abstract fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter)
    }

    private abstract class Written<E : IObjectData>(override val hash: ObjectHash) : State<E>() {
        override fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter) {}
    }

    private class Created<E : IObjectData>(val data: E) : State<E>() {
        override val hash by lazy(LazyThreadSafetyMode.PUBLICATION) { ObjectHash.computeHash(data.serialize()) }
        override val deserializer: IObjectDeserializer<E>
            get() = data.getDeserializer().upcast()

        override fun unload(ref: ObjectReferenceImpl<E>) {
            throw IllegalStateException("Object isn't persisted yet and would get lost: $data")
        }
        override fun load(ref: ObjectReferenceImpl<E>, serialized: E) {}
        override fun isLoaded(): Boolean = true
        override fun getLoadedData(): E = data
        override fun request(ref: ObjectReferenceImpl<E>): IStream.One<E> = IStream.Companion.of(data)
        override fun write(ref: ObjectReferenceImpl<E>, writer: IObjectWriter) {
            // Writer may try to unload this object after write, which wouldn't be allowed in the current state.
            // That's why the state change has to happen first.
            ref.state = Loaded(hash, data)
            try {
                writer.write(Object(data, ref))
            } catch (ex: Throwable) {
                ref.state = this
                throw ex
            }
            for (otherReference in data.getAllReferences()) {
                otherReference.write(writer)
            }
        }
        override fun toString(): String = "[created]$hash"
    }

    private class Loaded<E : IObjectData>(hash: ObjectHash, val data: E) : Written<E>(hash) {
        override val deserializer: IObjectDeserializer<E>
            get() = data.getDeserializer().upcast()
        override fun unload(ref: ObjectReferenceImpl<E>) {
            // State transition from loaded to unloaded is problematic in the world of immutable data.
            // A new reference instance should be created where the initial state is unloaded.
            throw UnsupportedOperationException()
            // ref.state = Unloaded(hash, data.getDeserializer().upcast())
        }
        override fun load(ref: ObjectReferenceImpl<E>, data: E) {}
        override fun isLoaded(): Boolean = true
        override fun getLoadedData(): E = data
        override fun request(ref: ObjectReferenceImpl<E>): IStream.One<E> = IStream.of(data)
        override fun toString(): String = "[loaded]$hash"
    }

    private class Unloaded<E : IObjectData>(hash: ObjectHash, override val deserializer: IObjectDeserializer<E>) : Written<E>(hash) {
        override fun unload(ref: ObjectReferenceImpl<E>) {}
        override fun load(ref: ObjectReferenceImpl<E>, data: E) {
            ref.state = Loaded(hash, data)
        }
        override fun isLoaded(): Boolean = false
        override fun getLoadedData(): E? = null
        override fun request(ref: ObjectReferenceImpl<E>): IStream.One<E> = ref.graph.request(ref)
        override fun toString(): String = "[unloaded]$hash"
    }
}
