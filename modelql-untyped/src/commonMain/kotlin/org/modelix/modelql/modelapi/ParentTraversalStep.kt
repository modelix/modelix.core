package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class ParentTraversalStep(): MonoTransformingStep<INode, INode>() {
    override fun transform(element: INode): Sequence<INode> {
        return sequenceOf(element.parent).filterNotNull()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.parent")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ParentTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.parent()"""
    }
}

fun IMonoStep<INode>.parent(): IMonoStep<INode> = ParentTraversalStep().also { connect(it) }
fun IFluxStep<INode>.parent(): IFluxStep<INode> = map { it.parent() }
