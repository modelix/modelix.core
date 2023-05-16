package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class ChildrenTraversalStep(val role: String?): FluxTransformingStep<INode, INode>() {
    override fun transform(element: INode): Sequence<INode> {
        return element.getChildren(role).asSequence()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = ChildrenStepDescriptor(role)

    @Serializable
    @SerialName("untyped.children")
    class ChildrenStepDescriptor(val role: String?) : StepDescriptor() {
        override fun createStep(): IStep {
            return ChildrenTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.children("$role")"""
    }
}

fun IProducingStep<INode>.children(role: String?): IFluxStep<INode> = ChildrenTraversalStep(role).also { connect(it) }