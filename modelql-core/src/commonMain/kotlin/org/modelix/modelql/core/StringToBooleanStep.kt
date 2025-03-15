package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class StringToBooleanStep : SimpleMonoTransformingStep<String?, Boolean>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.toBoolean() ?: false
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toBoolean")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return StringToBooleanStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.toBoolean()"
    }
}

fun IMonoStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
fun IFluxStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
