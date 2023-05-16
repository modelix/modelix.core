package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringToIntStep : MonoTransformingStep<String?, Int>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Int> {
        return serializersModule.serializer<Int>()
    }

    override fun transform(element: String?): Sequence<Int> {
        return sequenceOf(element?.toInt() ?: 0)
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toInt")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringToIntStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.toInt()"
    }
}

fun IMonoStep<String?>.toInt(): IMonoStep<Int> = StringToIntStep().also { connect(it) }