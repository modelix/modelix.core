package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class CollectionSizeStep : MonoTransformingStep<Collection<*>, Int>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer()
    }

    override fun transform(input: Collection<*>): Int {
        return input.size
    }

    override fun createDescriptor(context: QuerySerializationContext): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("size")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CollectionSizeStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.size()"
    }
}

fun IMonoStep<Collection<*>>.size() = CollectionSizeStep().connectAndDowncast(this)
