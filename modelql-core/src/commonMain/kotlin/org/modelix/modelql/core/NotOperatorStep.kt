package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class NotOperatorStep() : MonoTransformingStep<Boolean, Boolean>() {

    override fun transform(input: Boolean): Boolean {
        return !input
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor() = NotDescriptor()

    @Serializable
    @SerialName("not")
    class NotDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NotOperatorStep()
        }
    }

    override fun toString(): String {
        return "!${getProducers().single()}"
    }
}

operator fun IMonoStep<Boolean>.not(): IMonoStep<Boolean> = NotOperatorStep().also { connect(it) }
