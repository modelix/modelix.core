package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FirstElementStep<E>() : MonoTransformingStep<E, E>() {
    override fun canBeMultiple(): Boolean = false

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.take(1)
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun toString(): String {
        return "${getProducer()}\n.first()"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstElementStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = FirstElementDescriptor()
    }
}

fun <Out> IProducingStep<Out>.first(): IMonoStep<Out> {
    return FirstElementStep<Out>().also { it.connect(this) }
}
