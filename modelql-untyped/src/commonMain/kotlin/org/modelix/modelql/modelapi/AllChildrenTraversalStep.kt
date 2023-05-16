package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class AllChildrenTraversalStep(): FluxTransformingStep<INode, INode>() {
    override fun transform(element: INode): Sequence<INode> {
        return element.allChildren.asSequence()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = AllChildrenStepDescriptor()

    @Serializable
    @SerialName("untyped.allChildren")
    class AllChildrenStepDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return AllChildrenTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.allChildren()"""
    }
}

fun IProducingStep<INode>.allChildren(): IFluxStep<INode> = AllChildrenTraversalStep().also { connect(it) }