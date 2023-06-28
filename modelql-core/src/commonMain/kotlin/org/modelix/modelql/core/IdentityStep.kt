package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

open class IdentityStep<E> : TransformingStep<E, E>(), IFluxOrMonoStep<E> {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E> {
        return input
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return getProducer().createSequence(queryInput)
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun createDescriptor(): StepDescriptor {
        return IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducer()}.identity()"
    }

    @Serializable
    @SerialName("identity")
    class IdentityStepDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IdentityStep<Any?>()
        }
    }
}

fun <T> IMonoStep<T>.asFlux(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.identity(): IFluxStep<T> = IdentityStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.identity(): IMonoStep<T> = IdentityStep<T>().also { connect(it) }
