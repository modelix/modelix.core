package org.modelix.modelql.untyped

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.resolveIn
import org.modelix.model.api.serialize
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
        val nodeRef = NodeReference(serializedNodeRef!!)
        val conceptReference = conceptUID?.let { ConceptReference(it) }
        return if (conceptProvided) {
            createNode(nodeRef, conceptReference)
        } else {
            createNode(nodeRef)
        }
    }

    protected open fun createNode(ref: NodeReference): INode {
        return ref.resolveIn(ContextArea.getArea()!!) ?: throw RuntimeException("Failed to resolve node: $ref")
    }
    protected open fun createNode(ref: NodeReference, concept: ConceptReference?): INode {
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
