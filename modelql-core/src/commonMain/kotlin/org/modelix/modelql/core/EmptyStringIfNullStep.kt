package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class EmptyStringIfNullStep : MonoTransformingStep<String?, String>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> {
        return serializersModule.serializer<String>()
    }

    override fun transform(element: String?): Sequence<String> {
        return sequenceOf(element ?: "")
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("emptyStringIfNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return EmptyStringIfNullStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.emptyStringIfNull()"
    }
}

fun IMonoStep<String?>.emptyStringIfNull(): IMonoStep<String> = EmptyStringIfNullStep().also { connect(it) }