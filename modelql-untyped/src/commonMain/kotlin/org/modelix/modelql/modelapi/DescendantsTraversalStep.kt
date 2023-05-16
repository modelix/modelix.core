package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import org.modelix.modelql.core.*

class DescendantsTraversalStep(val includeSelf: Boolean): FluxTransformingStep<INode, INode>() {
    override fun transform(element: INode): Sequence<INode> {
        return element.getDescendants(includeSelf)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = if (includeSelf) WithSelfDescriptor() else WithoutSelfDescriptor()

    @Serializable
    @SerialName("untyped.descendantsAndSelf")
    class WithSelfDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return DescendantsTraversalStep(true)
        }
    }
    @Serializable
    @SerialName("untyped.descendants")
    class WithoutSelfDescriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return DescendantsTraversalStep(false)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.${if (includeSelf) "descendantsAndSelf" else "descendants"}()"""
    }
}

fun IProducingStep<INode>.descendants(includeSelf: Boolean = false): IFluxStep<INode> = DescendantsTraversalStep(includeSelf).also { connect(it) }