package org.modelix.datastructures.objects

interface IObjectDeserializer<out E : IObjectData> {
    fun deserialize(serialized: String, referenceFactory: IObjectReferenceFactory): E
}

fun <T : IObjectData> IObjectDeserializer<*>.upcast(): IObjectDeserializer<T> {
    @Suppress("UNCHECKED_CAST")
    return this as IObjectDeserializer<T>
}
