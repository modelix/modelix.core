package org.modelix.datastructures.objects

import org.modelix.kotlin.utils.base64UrlDecoded
import org.modelix.kotlin.utils.base64UrlEncoded

interface IDataTypeConfiguration<E> : Comparator<E> {
    fun serialize(element: E): String
    fun deserialize(serialized: String): E

    fun hashCode64(element: E): Long = hashCode32(element).toLong()
    fun hashCode32(element: E): Int

    fun getAllReferences(element: E): List<ObjectReference<IObjectData>> {
        return getContainmentReferences(element) + getNonContainmentReferences(element)
    }

    /**
     * Those are traversed during a diff and are expected to form a tree.
     * When the containment references are traversed recursively starting at the root,
     * each object should appear exactly once.
     */
    fun getContainmentReferences(element: E): List<ObjectReference<IObjectData>> = emptyList()

    /**
     * Additional references that violate the requirements for containment references.
     */
    fun getNonContainmentReferences(element: E): List<ObjectReference<IObjectData>> = emptyList()
}

class LongDataTypeConfiguration : IDataTypeConfiguration<Long> {
    override fun serialize(element: Long): String {
        return element.toULong().toString(16)
    }

    override fun deserialize(serialized: String): Long {
        return serialized.toULong(16).toLong()
    }

    override fun hashCode64(element: Long): Long {
        return element
    }

    override fun hashCode32(element: Long): Int {
        return element.hashCode()
    }

    override fun compare(a: Long, b: Long): Int {
        return a.compareTo(b)
    }
}

class StringDataTypeConfiguration : IDataTypeConfiguration<String> {
    override fun serialize(element: String): String = element
    override fun deserialize(serialized: String): String = serialized
    override fun hashCode32(element: String): Int = element.hashCode()
    override fun compare(a: String, b: String): Int = a.compareTo(b)
}

class Base64DataTypeConfiguration<E>(val wrapped: IDataTypeConfiguration<E>) : IDataTypeConfiguration<E> by wrapped {
    override fun serialize(element: E): String = wrapped.serialize(element).base64UrlEncoded
    override fun deserialize(serialized: String): E = wrapped.deserialize(serialized.base64UrlDecoded)
}
