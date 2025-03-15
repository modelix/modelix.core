package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TakeStep<E>(val count: Int) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.take(count)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.take($count)"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(count)

    @Serializable
    @SerialName("take")
    data class Descriptor(val count: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return TakeStep<Any?>(count)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(count)
    }
}

fun <T> IFluxStep<T>.take(count: Int): IFluxStep<T> {
    return TakeStep<T>(count).also { connect(it) }
}
