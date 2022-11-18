package org.modelix.model.api

interface INodeReferenceSerializer {

    fun serialize(ref: INodeReference): String?
    fun deserialize(serialized: String): INodeReference?

    companion object {
        private val serializers: MutableSet<INodeReferenceSerializer> = HashSet()

        init {
            register(ByIdSerializer)
        }

        fun register(serializer: INodeReferenceSerializer) {
            serializers.add(serializer)
        }

        fun unregister(serializer: INodeReferenceSerializer) {
            serializers.remove(serializer)
        }

        fun serialize(ref: INodeReference): String {
            if (ref is SerializedNodeReference) return ref.serialized
            return serializers.map { it.serialize(ref) }.firstOrNull { it != null }
                ?: throw RuntimeException("No serializer found for ${ref::class}")
        }

        fun deserialize(serialized: String): INodeReference {
            return serializers.map { it.deserialize(serialized) }.firstOrNull { it != null }
                ?: throw RuntimeException("No deserializer found for: $serialized")
        }
    }
}

private object ByIdSerializer : INodeReferenceSerializer {
    const val PREFIX = "id:"
    override fun serialize(ref: INodeReference): String? {
        return if (ref is NodeReferenceById) PREFIX + ref.nodeId else null
    }

    override fun deserialize(serialized: String): INodeReference? {

        return if (serialized.startsWith(PREFIX)) NodeReferenceById(serialized.drop(PREFIX.length)) else null
    }
}