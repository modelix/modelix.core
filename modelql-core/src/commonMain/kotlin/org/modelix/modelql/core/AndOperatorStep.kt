package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class AndOperatorStep() : MonoTransformingStep<IZipOutput<Boolean>, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: IZipOutput<Boolean>): Boolean {
        return input.values.all { it == true }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("and")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AndOperatorStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "and(" + getProducers().joinToString(", ") + ")"
    }
}

infix fun IMonoStep<Boolean>.and(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return AndOperatorStep().connectAndDowncast(zip)
}
