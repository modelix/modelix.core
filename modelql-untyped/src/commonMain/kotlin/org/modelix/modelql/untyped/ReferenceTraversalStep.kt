package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLinkReference
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
import org.modelix.modelql.core.map
import org.modelix.modelql.core.orNull
import org.modelix.modelql.core.stepOutputSerializer

class ReferenceTraversalStep(val link: IReferenceLinkReference) : MonoTransformingStep<INode, INode>(), IMonoStep<INode> {
    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.flatMap {
            it.value.asAsyncNode().getReferenceTarget(link).map { it.asRegularNode() }
        }.asStepStream(this)
    }

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(link.getIdOrName(), link)

    @Serializable
    @SerialName("untyped.referenceTarget")
    data class Descriptor(val role: String, val link: IReferenceLinkReference? = null) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ReferenceTraversalStep(link ?: IReferenceLinkReference.fromString(role))
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(role, link)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.reference(\"$link\")"
    }
}
fun IMonoStep<INode>.reference(role: IReferenceLinkReference) = ReferenceTraversalStep(role).connectAndDowncast(this)
fun IFluxStep<INode>.reference(role: IReferenceLinkReference) = ReferenceTraversalStep(role).connectAndDowncast(this)
fun IMonoStep<INode>.referenceOrNull(role: IReferenceLinkReference): IMonoStep<INode?> = reference(role).orNull()
fun IFluxStep<INode>.referenceOrNull(role: IReferenceLinkReference): IFluxStep<INode?> = map { it.referenceOrNull(role) }

@Deprecated("provide an IReferenceLinkReference")
fun IMonoStep<INode>.reference(role: String) = reference(IReferenceLinkReference.fromString(role))

@Deprecated("provide an IReferenceLinkReference")
fun IFluxStep<INode>.reference(role: String) = reference(IReferenceLinkReference.fromString(role))

@Deprecated("provide an IReferenceLinkReference")
fun IMonoStep<INode>.referenceOrNull(role: String): IMonoStep<INode?> = referenceOrNull(IReferenceLinkReference.fromString(role))

@Deprecated("provide an IReferenceLinkReference")
fun IFluxStep<INode>.referenceOrNull(role: String): IFluxStep<INode?> = referenceOrNull(IReferenceLinkReference.fromString(role))
