package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class IdentityStep<E> : TransformingStep<E, E>(), IFluxOrMonoStep<E> {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducer()}"
    }

    @Serializable
    @SerialName("identity")
    class IdentityStepDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IdentityStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = IdentityStepDescriptor()
    }
}

fun <T> IMonoStep<T>.asFlux(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.identity(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.identity(): IMonoStep<T> = IdentityStep<T>().also { connect(it) }
