package org.modelix.model.objects

import org.modelix.streams.IStream

class Object<out E : IObjectData>(val data: E, val ref: ObjectReference<E>) {
    operator fun component1() = data
    operator fun component2() = ref

    fun getHash(): ObjectHash = ref.getHash()
    fun getHashString(): String = ref.getHash().toString()

    fun objectDiff(oldObject: Object<*>?, loader: IObjectLoader): IStream.Many<Object<*>> {
        return if (oldObject == null) {
            getDescendantsAndSelf(loader)
        } else if (ref.getHash() == oldObject.ref.getHash()) {
            IStream.empty()
        } else {
            return data.objectDiff(this, oldObject, loader)
        }
    }

    override fun equals(other: Any?): Boolean {
        error("Use .getHash() for comparing objects")
    }

    override fun hashCode(): Int {
        error("Use .getHash() for comparing objects")
    }
}

fun <T : IObjectData> T.asObject(): Object<T> = Object(this, ObjectReference(this))
