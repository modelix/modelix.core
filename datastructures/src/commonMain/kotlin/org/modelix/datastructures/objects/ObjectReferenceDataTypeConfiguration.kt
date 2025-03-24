package org.modelix.datastructures.objects

class ObjectReferenceDataTypeConfiguration<E : IObjectData>(
    val graph: IObjectGraph,
    val deserializer: IObjectDeserializer<E>,
) : IDataTypeConfiguration<ObjectReference<E>> {

    override fun serialize(element: ObjectReference<E>): String {
        return element.getHashString()
    }

    override fun deserialize(serialized: String): ObjectReference<E> {
        return graph.fromHashString(serialized, deserializer)
    }

    override fun hashCode32(element: ObjectReference<E>): Int {
        return element.getHash().hashCode()
    }

    override fun compare(a: ObjectReference<E>, b: ObjectReference<E>): Int {
        return a.getHashString().compareTo(b.getHashString())
    }

    override fun getContainmentReferences(element: ObjectReference<E>): List<ObjectReference<IObjectData>> {
        return listOf(element)
    }
}
