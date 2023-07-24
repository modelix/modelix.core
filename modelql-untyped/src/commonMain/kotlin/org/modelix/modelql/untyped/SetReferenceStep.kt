package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.key
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.connect

class SetReferenceStep(val role: String) :
    TransformingStepWithParameter<INode, INode?, INode?, INode>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializersModule)
    }

    override fun transformElement(input: INode, parameter: INode?): INode {
        input.setReferenceTarget(input.resolveReferenceLinkOrFallback(role), parameter)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(role)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setReference($role, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.setReference")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SetReferenceStep(role)
        }
    }
}

fun IMonoStep<INode>.setReference(role: String, target: IMonoStep<INode?>): IMonoStep<INode> {
    return SetReferenceStep(role).also {
        connect(it)
        target.connect(it)
    }
}
fun IMonoStep<INode>.setReference(role: IReferenceLink, target: IMonoStep<INode?>): IMonoStep<INode> {
    return setReference(role.key(), target)
}
