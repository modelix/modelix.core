package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DropStep<E>(val count: Int) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.skip(count.toLong())
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.drop($count)"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(count)

    @Serializable
    @SerialName("drop")
    data class Descriptor(val count: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DropStep<Any?>(count)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(count)
    }
}

fun <T> IFluxStep<T>.drop(count: Int): IFluxStep<T> {
    return DropStep<T>(count).also { connect(it) }
}
