package org.modelix.modelql.core

import kotlinx.coroutines.flow.single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class SingleStep<E>() : AggregationStep<E, E>() {

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<E> {
        return input.single()
    }

    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<E> {
        return input.single()
    }

    override fun toString(): String {
        return "${getProducer()}.single()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("single")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SingleStep<Any?>()
        }
    }
}

fun <E> IFluxStep<E>.single(): IMonoStep<E> {
    return SingleStep<E>().also { connect(it) }
}
