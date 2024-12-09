package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class NotOperatorStep() : SimpleMonoTransformingStep<Boolean, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: Boolean): Boolean {
        return !input
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = NotDescriptor()

    @Serializable
    @SerialName("not")
    class NotDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NotOperatorStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = NotDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.not()"
    }
}

operator fun IMonoStep<Boolean>.not(): IMonoStep<Boolean> = NotOperatorStep().also { connect(it) }
