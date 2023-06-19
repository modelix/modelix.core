package org.modelix.modelql.untyped

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.key
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.modelql.core.IConsumingStep
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect

class SetReferenceStep(val role: String) : MonoTransformingStep<INode?, INode>(), IConsumingStep<INode?> {
    private var targetProducer: IProducingStep<INode?>? = null

    override fun getProducers(): List<IProducingStep<INode?>> {
        return super.getProducers() + listOfNotNull(targetProducer)
    }

    override fun addProducer(producer: IProducingStep<INode?>) {
        if (getProducers().isEmpty()) super.addProducer(producer) else targetProducer = producer
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createFlow(input: Flow<INode?>, context: IFlowInstantiationContext): Flow<INode> {
        val targetFlow: Flow<INode?> = context.getOrCreateFlow(targetProducer!!).onEmpty { emit(null) }
        return input.combine(targetFlow) { source, target ->
            source!!.setReferenceTarget(source!!.resolveReferenceLinkOrFallback(role), target)
            source
        }
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(role)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setReference($role, $targetProducer)"
    }

    @Serializable
    @SerialName("untyped.setReference")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
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
