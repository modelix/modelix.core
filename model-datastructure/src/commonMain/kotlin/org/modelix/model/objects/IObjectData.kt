package org.modelix.model.objects

import org.modelix.streams.IStream
import org.modelix.streams.plus

/**
 * Implementations should be data classes (immutable and implement equals/hashCode)
 */
interface IObjectData {
    fun serialize(): String
    fun getDeserializer(): IObjectDeserializer<*>
    fun getAllReferences(): List<ObjectReference<IObjectData>> = getContainmentReferences() + getNonContainmentReferences()
    fun getContainmentReferences(): List<ObjectReference<IObjectData>>
    fun getNonContainmentReferences(): List<ObjectReference<IObjectData>> = emptyList()

    /**
     * Should not be called directly, but by calling [ObjectReference.diff].
     *
     * Callers should compare the hashes of the old and new object before calling this method.
     * It assumes that the [oldObject] is different from this one and calling it anyway will result in a bigger diff.
     */
    fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        requireDifferentHash(oldObject?.data)
        return self.getDescendantsAndSelf()
    }
}

@Deprecated("Just don't use it and assume they are different")
fun IObjectData.requireDifferentHash(other: IObjectData?) {
    requireDifferentHash(other?.hash)
}

@Deprecated("Just don't use it and assume they are different")
fun IObjectData.requireDifferentHash(other: Object<*>?) {
    requireDifferentHash(other?.ref?.getHash())
}

@Deprecated("Just don't use it and assume they are different")
fun IObjectData.requireDifferentHash(other: ObjectHash?) {
    require(other != this.hash) {
        "Object hashes should be compared before calling this method. This check will be removed in the future."
    }
}

@Deprecated("To avoid unnecessary expensive hash computation, hashes should be retrieved from ObjectReference")
val IObjectData.hash: ObjectHash get() = ObjectHash.computeHash(serialize())

@Deprecated("To avoid unnecessary expensive hash computation, hashes should be retrieved from ObjectReference")
val IObjectData.hashString: String get() = hash.toString()

fun IObjectData.getDescendantRefs(): IStream.Many<ObjectReference<*>> {
    return IStream.many(getContainmentReferences())
        .flatMap { it.getDescendantRefsAndSelf() }
}

fun ObjectReference<*>.getDescendantRefsAndSelf(): IStream.Many<ObjectReference<*>> {
    return IStream.of(this) + this.resolveData().flatMap { it.getDescendantRefs() }
}

fun <T : IObjectData> IObjectData.upcast(): T {
    @Suppress("UNCHECKED_CAST")
    return this as T
}
