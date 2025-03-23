package org.modelix.datastructures.objects

interface IObjectReferenceFactory {
    operator fun <T : IObjectData> invoke(hash: String, deserializer: IObjectDeserializer<T>): ObjectReference<T> {
        return fromHashString(hash, deserializer)
    }
    operator fun <T : IObjectData> invoke(data: T): ObjectReference<T> {
        return fromCreated(data)
    }

    fun <T : IObjectData> fromHashString(hash: String, deserializer: IObjectDeserializer<T>): ObjectReference<T> {
        return fromHash(ObjectHash(hash), deserializer)
    }

    fun <T : IObjectData> fromHash(hash: ObjectHash, deserializer: IObjectDeserializer<T>): ObjectReference<T>
    fun <T : IObjectData> fromDeserialized(hash: ObjectHash, data: T): ObjectReference<T>
    fun <T : IObjectData> fromCreated(data: T): ObjectReference<T>
}
