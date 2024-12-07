package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OrOperatorStep() : SimpleMonoTransformingStep<IZipOutput<Boolean>, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: IZipOutput<Boolean>): Boolean {
        return input.values.any { it == true }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("or")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return OrOperatorStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "or(\n" + (getProducer() as ZipStep<*, *>).getProducers().joinToString(",") { it.toString().prependIndent("  ") } + "\n)"
    }
}

infix fun IMonoStep<Boolean>.or(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return OrOperatorStep().connectAndDowncast(zip)
}
