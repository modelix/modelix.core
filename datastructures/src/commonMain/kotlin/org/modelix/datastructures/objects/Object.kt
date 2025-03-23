package org.modelix.datastructures.objects

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream
import org.modelix.streams.plus

open class Object<out E : IObjectData>(val data: E, val ref: ObjectReference<E>) {
    val graph: IObjectGraph get() = ref.graph
    val referenceFactory: IObjectReferenceFactory get() = ref.graph

    operator fun component1() = data
    operator fun component2() = ref

    fun getHash(): ObjectHash = ref.getHash()
    fun getHashString(): String = ref.getHash().toString()

    fun write() {
        ref.write()
    }

    fun objectDiff(oldObject: Object<*>?): IStream.Many<Object<*>> {
        return if (oldObject == null) {
            getDescendantsAndSelf()
        } else if (ref.getHash() == oldObject.ref.getHash()) {
            IStream.empty()
        } else {
            return data.objectDiff(this, oldObject)
        }
    }

    override fun equals(other: Any?): Boolean {
        error("Use .getHash() for comparing objects")
    }

    override fun hashCode(): Int {
        error("Use .getHash() for comparing objects")
    }
}

/**
 * Make sure this is only called on newly created objects and not on a deserialized one where a known hash exists.
 * Otherwise, this object may be written back to the store in situations where writing isn't expected (e.g. in a read
 * transaction).
 * Provide the hash if the object was just deserialized.
 */
@DelicateModelixApi
fun <T : IObjectData> T.asObject(factory: IObjectReferenceFactory): Object<T> = Object(this, factory(this))

fun <T : IObjectData> T.asObject(hash: ObjectHash, factory: IObjectReferenceFactory): Object<T> =
    Object(this, factory.fromDeserialized(hash, this))

fun Object<*>.getDescendants(): IStream.Many<Object<*>> {
    return IStream.many(data.getContainmentReferences())
        .flatMap {
            it.resolve().flatMap { it.getDescendantsAndSelf() }
        }
}

fun Object<*>.getDescendantsAndSelf(): IStream.Many<Object<*>> {
    return IStream.of(this) + getDescendants()
}

fun <T : IObjectData> Object<*>.upcast(): Object<T> {
    @Suppress("UNCHECKED_CAST")
    return this as Object<T>
}
