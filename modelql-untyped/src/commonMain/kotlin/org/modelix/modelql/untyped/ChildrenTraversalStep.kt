package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepStream
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class ChildrenTraversalStep(val link: IChildLinkReference) : FluxTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.flatMap { it.value.asAsyncNode().getChildren(link).map { it.asRegularNode() } }.asStepStream(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = ChildrenStepDescriptor(link.getIdOrNameOrNull(), link)

    @Serializable
    @SerialName("untyped.children")
    data class ChildrenStepDescriptor(val role: String?, val link: IChildLinkReference? = null) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ChildrenTraversalStep(link ?: IChildLinkReference.fromUnclassifiedString(role))
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = ChildrenStepDescriptor(role, link)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.children(\"$link\")"
    }
}

fun IProducingStep<INode>.children(role: IChildLinkReference): IFluxStep<INode> = ChildrenTraversalStep(role).also { connect(it) }

@Deprecated("provide an IChildLinkReference")
fun IProducingStep<INode>.children(role: String?): IFluxStep<INode> = children(IChildLinkReference.fromUnclassifiedString(role))
