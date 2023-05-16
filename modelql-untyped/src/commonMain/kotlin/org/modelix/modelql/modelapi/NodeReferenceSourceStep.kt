package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.serialize
import org.modelix.modelql.core.*

class NodeReferenceSourceStep(element: SerializedNodeReference) : ConstantSourceStep<SerializedNodeReference>(element) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<SerializedNodeReference> {
        return serializersModule.serializer<SerializedNodeReference>()
    }

    override fun createDescriptor() = Descriptor(element)

    @Serializable
    @SerialName("nodeReferenceMonoSource")
    class Descriptor(val element: SerializedNodeReference) : StepDescriptor() {
        override fun createStep(): IStep {
            return NodeReferenceSourceStep(element)
        }
    }

    override fun toString(): String {
        return "<${element.serialized}>"
    }
}

fun INodeReference.asMono(): IMonoStep<SerializedNodeReference> = NodeReferenceSourceStep(SerializedNodeReference(serialize()))