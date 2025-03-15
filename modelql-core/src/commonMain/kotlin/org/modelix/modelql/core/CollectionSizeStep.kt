package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CollectionSizeStep : SimpleMonoTransformingStep<Collection<*>, Int>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: Collection<*>): Int {
        return input.size
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("size")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CollectionSizeStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.size()"
    }
}

fun IMonoStep<Collection<*>>.size() = CollectionSizeStep().connectAndDowncast(this)
