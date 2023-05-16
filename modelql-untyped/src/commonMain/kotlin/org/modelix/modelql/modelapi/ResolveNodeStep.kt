package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.ContextArea
import org.modelix.modelql.core.*

class ResolveNodeStep(): MonoTransformingStep<INodeReference, INode>() {
    override fun transform(element: INodeReference): Sequence<INode> {
        val node = element.resolveNode(ContextArea.getArea()) ?: throw RuntimeException("Node not found: $element")
        return sequenceOf(node)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.resolveNode")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ResolveNodeStep()
        }
    }

    override fun toString(): String {
        return getProducers().single().toString() + ".resolve()"
    }
}

fun IMonoStep<INodeReference>.resolve(): IMonoStep<INode> = ResolveNodeStep().also { connect(it) }
fun IFluxStep<INodeReference>.resolve(): IFluxStep<INode> = map { it.resolve() }
