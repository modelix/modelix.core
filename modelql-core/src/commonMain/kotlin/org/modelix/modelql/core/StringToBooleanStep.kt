package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringToBooleanStep : MonoTransformingStep<String?, Boolean>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun transform(input: String?): Boolean {
        return input?.toBoolean() ?: false
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toBoolean")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringToBooleanStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.toBoolean()"
    }
}

fun IMonoStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
fun IFluxStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
