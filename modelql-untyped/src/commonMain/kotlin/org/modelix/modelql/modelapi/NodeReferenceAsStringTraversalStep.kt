package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.serialize
import org.modelix.modelql.core.*

class NodeReferenceAsStringTraversalStep(): MonoTransformingStep<INodeReference, String>() {
    override fun transform(element: INodeReference): Sequence<String> {
        return sequenceOf(element.serialize())
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> {
        return serializersModule.serializer<String>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("nodeReferenceAsString")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return NodeReferenceAsStringTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.asString()"""
    }
}


fun IMonoStep<INodeReference>.asString(): IMonoStep<String> = NodeReferenceAsStringTraversalStep().also { connect(it) }
fun IFluxStep<INodeReference>.asString(): IFluxStep<String> = map { it.asString() }

fun IMonoStep<INode>.nodeReferenceAsString(): IMonoStep<String> = nodeReference().asString()
fun IFluxStep<INode>.nodeReferenceAsString(): IFluxStep<String> = nodeReference().asString()
