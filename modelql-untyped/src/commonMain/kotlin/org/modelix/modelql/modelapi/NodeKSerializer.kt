package org.modelix.modelql.modelapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.modelix.model.api.*
import org.modelix.model.area.ContextArea

open class NodeKSerializer() : KSerializer<INode> {
    @OptIn(InternalSerializationApi::class)
    private val conceptUIDSerializer = String::class.serializer().nullable

    override fun deserialize(decoder: Decoder): INode {
        var serializedNodeRef: String? = null
        var conceptUID: String? = null
        var conceptProvided: Boolean = false
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> serializedNodeRef = decodeStringElement(descriptor, 0)
                    1 -> {
                        conceptUID = decodeSerializableElement(descriptor, 1, conceptUIDSerializer)
                        conceptProvided = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected index: $index")
                }
            }
        }
        val nodeRef = SerializedNodeReference(serializedNodeRef!!)
        val conceptReference = conceptUID?.let { ConceptReference(it) }
        return if (conceptProvided) {
            createNode(nodeRef, conceptReference)
        } else {
            createNode(nodeRef)
        }
    }

    protected open fun createNode(ref: SerializedNodeReference): INode {
        return ref.resolveNode(ContextArea.getArea()) ?: throw RuntimeException("Failed to resolve node: $ref")
    }
    protected open fun createNode(ref: SerializedNodeReference, concept: ConceptReference?): INode {
        return createNode(ref)
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("modelix.INode", StructureKind.OBJECT) {
        element<String>("node", isOptional = false)
        element("concept", isOptional = true, descriptor = conceptUIDSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: INode) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.reference.serialize())
            value.getConceptReference()?.let {
                encodeStringElement(descriptor, 1, it.getUID())
            }
        }
    }
}