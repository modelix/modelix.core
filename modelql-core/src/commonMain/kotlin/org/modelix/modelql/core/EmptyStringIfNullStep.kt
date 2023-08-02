package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class EmptyStringIfNullStep : MonoTransformingStep<String?, String>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String>> {
        return serializersModule.serializer<String>().stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): String {
        return input ?: ""
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("emptyStringIfNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return EmptyStringIfNullStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.emptyStringIfNull()"
    }
}

fun IMonoStep<String?>.emptyStringIfNull() = EmptyStringIfNullStep().connectAndDowncast(this)
fun IFluxStep<String?>.emptyStringIfNull() = EmptyStringIfNullStep().connectAndDowncast(this)
