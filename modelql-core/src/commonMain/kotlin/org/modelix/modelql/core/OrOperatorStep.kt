package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class OrOperatorStep() : MonoTransformingStep<IZipOutput<Boolean>, Boolean>() {

    override fun transform(input: IZipOutput<Boolean>): Boolean {
        return input.values.any { it == true }
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    @Serializable
    @SerialName("or")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return OrOperatorStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun toString(): String {
        return "or(" + (getProducer() as ZipStep<*, *>).getProducers().joinToString(", ") + ")"
    }
}

infix fun IMonoStep<Boolean>.or(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return OrOperatorStep().connectAndDowncast(zip)
}
