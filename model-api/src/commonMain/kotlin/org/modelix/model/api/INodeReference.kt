package org.modelix.model.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.model.area.IArea

/**
 * Reference to an [INode]-
 *
 * This class used to be an interface, but was changed to an abstract class for being able to overriding
 * equals/hashCode. The semantics changed slightly to a unique identifier of a node. Implementing equals/hashCode in a
 * consistent way is necessary for using this class as the key of map entries.
 */
@Serializable(with = NodeReferenceKSerializer::class)
abstract class INodeReference {
    /**
     * Tries to find the referenced node in the given [IArea].
     *
     * @param area area to be searched in
     * @return the node, or null if the node could not be found
     */
    @Deprecated("use .resolveIn(INodeResolutionScope)", ReplaceWith("resolveIn(area!!)"))
    open fun resolveNode(area: IArea?): INode? = resolveIn(area as INodeResolutionScope)

    abstract fun serialize(): String

    final override fun equals(other: Any?): Boolean {
        return other is INodeReference && serialize() == other.serialize()
    }

    final override fun hashCode(): Int {
        return serialize().hashCode()
    }
}

fun INodeReference.resolveInCurrentContext(): INode? {
    return resolveIn(INodeResolutionScope.getCurrentScope())
}

fun INodeReference.resolveIn(scope: INodeResolutionScope): INode? {
    try {
        if (this is NodeReference) {
            val deserialized = INodeReferenceSerializer.tryDeserialize(serialized)
            if (deserialized != null) return deserialized.resolveIn(scope)
        }
        @Suppress("DEPRECATION")
        return scope.resolveNode(this)
    } catch (ex: Exception) {
        throw RuntimeException("Failed to resolve $this", ex)
    }
}

fun INodeReference.toSerialized(): NodeReference = if (this is NodeReference) this else NodeReference(this.serialize())

class NodeReferenceKSerializer : KSerializer<INodeReference> {
    override fun deserialize(decoder: Decoder): INodeReference {
        val serialized = decoder.decodeString()
        return INodeReferenceSerializer.tryDeserialize(serialized) ?: SerializedNodeReference(serialized)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("modelix.INodeReference", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: INodeReference) {
        encoder.encodeString(value.serialize())
    }
}

class UnresolvableNodeReferenceException(val reference: INodeReference) : IllegalArgumentException("Node not found: $reference")
