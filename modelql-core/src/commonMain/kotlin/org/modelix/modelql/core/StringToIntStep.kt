package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringToIntStep : MonoTransformingStep<String?, Int>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer()
    }

    override fun transform(input: String?): Int {
        return input?.toInt() ?: 0
    }

    override fun createDescriptor(context: QuerySerializationContext): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toInt")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return StringToIntStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.toInt()"
    }
}

fun IMonoStep<String?>.toInt() = StringToIntStep().connectAndDowncast(this)
fun IFluxStep<String?>.toInt() = StringToIntStep().connectAndDowncast(this)
