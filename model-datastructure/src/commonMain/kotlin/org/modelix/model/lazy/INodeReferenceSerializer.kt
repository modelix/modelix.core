package org.modelix.model.lazy

import org.modelix.model.api.INodeReference

@Deprecated("use org.modelix.model.api.INodeReferenceSerializer")
interface INodeReferenceSerializer : org.modelix.model.api.INodeReferenceSerializer {

    override fun serialize(ref: INodeReference): String?
    override fun deserialize(serialized: String): INodeReference?

    companion object {

        fun register(serializer: INodeReferenceSerializer) {
            org.modelix.model.api.INodeReferenceSerializer.register(serializer)
        }

        fun unregister(serializer: INodeReferenceSerializer) {
            org.modelix.model.api.INodeReferenceSerializer.unregister(serializer)
        }

        fun serialize(ref: INodeReference): String {
            return org.modelix.model.api.INodeReferenceSerializer.serialize(ref)
        }

        fun deserialize(serialized: String): INodeReference {
            return org.modelix.model.api.INodeReferenceSerializer.deserialize(serialized)
        }
    }
}
