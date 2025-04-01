package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.connect

class SetReferenceStep(val link: IReferenceLinkReference) :
    TransformingStepWithParameter<INode, INode?, INode?, INode>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializationContext)
    }

    override fun transformElement(input: IStepOutput<INode>, parameter: IStepOutput<INode?>?): IStepOutput<INode> {
        input.value.setReferenceTarget(link.toLegacy(), parameter?.value)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(link.getIdOrName(), link)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}\n.setReference($link, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.setReference")
    data class Descriptor(val role: String, val link: IReferenceLinkReference?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SetReferenceStep(link ?: IReferenceLinkReference.fromString(role))
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(role, link)
    }
}

fun IMonoStep<INode>.setReference(role: IReferenceLinkReference, target: IMonoStep<INode?>): IMonoStep<INode> {
    return SetReferenceStep(role).also {
        connect(it)
        target.connect(it)
    }
}

@Deprecated("provide an IReferenceLinkReference")
fun IMonoStep<INode>.setReference(role: String, target: IMonoStep<INode?>): IMonoStep<INode> {
    return setReference(IReferenceLinkReference.fromString(role), target)
}

@Deprecated("provide an IReferenceLinkReference")
fun IMonoStep<INode>.setReference(role: IReferenceLink, target: IMonoStep<INode?>): IMonoStep<INode> {
    return setReference(role.toReference(), target)
}
