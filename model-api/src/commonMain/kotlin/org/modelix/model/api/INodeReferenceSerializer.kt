package org.modelix.model.api

import kotlin.reflect.KClass

/**
 * Serializer for [INodeReference]s.
 */
@Deprecated("INodeResolutionScope.resolveNode(INodeReference) is now responsible for deserializing supported references")
interface INodeReferenceSerializer {

    /**
     * Serializes the given node reference.
     *
     * @param ref the node reference to be serialized
     * @return node reference as serialized string
     */
    fun serialize(ref: INodeReference): String?

    /**
     * Deserializes the given string, which represents a serialized node reference.
     *
     * @param string the node reference in serialized form
     * @return deserialized node reference
     */
    fun deserialize(serialized: String): INodeReference?

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
        private val deserializerForPrefix: MutableMap<String, INodeReferenceSerializerEx> = HashMap()
        private val serializersForClass: MutableMap<KClass<*>, INodeReferenceSerializerEx> = HashMap()
        private val legacySerializers: MutableSet<INodeReferenceSerializer> = HashSet()

        fun register(serializer: INodeReferenceSerializer) {
            register(serializer, true)
        }

        fun register(serializer: INodeReferenceSerializer, replace: Boolean) {
            if (serializer is INodeReferenceSerializerEx) {
                val prefix = serializer.prefix
                val existingDeserializer = deserializerForPrefix[prefix]
                if (existingDeserializer != null) {
                    if (replace) {
                        unregister(existingDeserializer)
                    } else {
                        throw IllegalStateException("Deserializer for '$prefix:' already registered: $existingDeserializer")
                    }
                }
                val supportedClasses = serializer.supportedReferenceClasses
                for (supportedClass in supportedClasses) {
                    val existingSerializer = serializersForClass[supportedClass]
                    if (existingSerializer != null) {
                        if (replace) {
                            unregister(existingSerializer)
                        } else {
                            throw IllegalStateException("Serializer for $supportedClass already registered: $existingSerializer")
                        }
                    }
                }
                deserializerForPrefix[prefix] = serializer
                supportedClasses.forEach { serializersForClass[it] = serializer }
            } else {
                LOG.warn { "Migrate ${serializer::class} to INodeReferenceSerializerEx" }
                legacySerializers.add(serializer)
            }
        }

        fun unregister(serializer: INodeReferenceSerializer) {
            if (serializer is INodeReferenceSerializerEx) {
                val prefix = serializer.prefix
                if (deserializerForPrefix[prefix] == serializer) {
                    deserializerForPrefix.remove(prefix)
                }
                for (supportedClass in serializer.supportedReferenceClasses) {
                    if (serializersForClass[supportedClass] == serializer) {
                        serializersForClass.remove(supportedClass)
                    }
                }
            } else {
                legacySerializers.remove(serializer)
            }
        }

        fun serialize(ref: INodeReference): String {
            if (ref is SerializedNodeReference) return ref.serialized

            val serializer = serializersForClass[ref::class]
            return if (serializer != null) {
                serializer.prefix + INodeReferenceSerializerEx.SEPARATOR + serializer.serialize(ref)
            } else {
                legacySerializers.map { it.serialize(ref) }.firstOrNull { it != null }
                    ?: ref.serialize()
            }
        }

        fun deserialize(serialized: String): INodeReference {
            return tryDeserialize(serialized) ?: SerializedNodeReference(serialized)
        }

        fun tryDeserialize(serialized: String): INodeReference? {
            val parts = serialized.split(INodeReferenceSerializerEx.SEPARATOR, limit = 2)
            if (parts.size == 2) {
                val deserializer = deserializerForPrefix[parts[0]]
                if (deserializer != null) {
                    return deserializer.deserialize(parts[1])
                }
            }

            return legacySerializers.map { it.deserialize(serialized) }.firstOrNull { it != null }
        }
    }
}

@Deprecated("INodeResolutionScope.resolveNode(INodeReference) is now responsible for deserializing supported references")
interface INodeReferenceSerializerEx : INodeReferenceSerializer {
    val prefix: String
    val supportedReferenceClasses: Set<KClass<out INodeReference>>
    override fun serialize(ref: INodeReference): String
    override fun deserialize(serialized: String): INodeReference

    companion object {
        const val SEPARATOR = ":"
    }
}
