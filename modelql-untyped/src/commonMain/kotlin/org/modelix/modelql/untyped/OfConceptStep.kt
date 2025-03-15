package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.getAllSubConcepts
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

class OfConceptStep(val conceptUIDs: Set<String>) : MonoTransformingStep<INode?, INode>() {
    override fun createStream(input: StepStream<INode?>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.filter {
            val value = it.value
            value != null && conceptUIDs.contains(value.getConceptReference()?.getUID())
        } as StepStream<INode>
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return getProducer().getOutputSerializer(serializationContext) as KSerializer<out IStepOutput<INode>>
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(conceptUIDs)

    @Serializable
    @SerialName("untyped.ofConcept")
    class Descriptor(val conceptUIDs: Set<String>) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return OfConceptStep(conceptUIDs)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(conceptUIDs)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.ofConcept($conceptUIDs)"
    }
}

fun IMonoStep<INode?>.ofConcept(concept: IConcept): IMonoStep<INode> {
    val subconceptUIDs = concept.getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return OfConceptStep(subconceptUIDs).connectAndDowncast(this)
}
fun IFluxStep<INode?>.ofConcept(concept: IConcept): IFluxStep<INode> {
    val subconceptUIDs = concept.getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return OfConceptStep(subconceptUIDs).connectAndDowncast(this)
}
