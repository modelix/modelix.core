package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepStream
import org.modelix.modelql.core.stepOutputSerializer

class LinkInParentTraversalStep() : MonoTransformingStep<INode, IChildLinkReference>() {

    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<IChildLinkReference> {
        return input.flatMap { it.value.asAsyncNode().getRoleInParent() }.asStepStream(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<IChildLinkReference>> {
        return serializationContext.serializer<IChildLinkReference>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("untyped.linkInParent")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RoleInParentTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return """${getProducers().single()}\n.linkInParent()"""
    }
}

fun IMonoStep<INode>.linkInParent() = LinkInParentTraversalStep().connectAndDowncast(this)
fun IFluxStep<INode>.linkInParent() = LinkInParentTraversalStep().connectAndDowncast(this)
