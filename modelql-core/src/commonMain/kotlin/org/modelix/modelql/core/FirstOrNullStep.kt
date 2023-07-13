package org.modelix.modelql.core

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FirstOrNullStep<E>() : AggregationStep<E, E?>() {

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<E?> {
        return input.firstOrNull() ?: null.asStepOutput()
    }

    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<E?> {
        return input.firstOrNull() ?: null.asStepOutput()
    }

    override fun toString(): String {
        return "${getProducer()}.firstOrNull()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E?>> {
        return getProducer().getOutputSerializer(serializersModule).upcast()
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    @Serializable
    @SerialName("firstOrNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstOrNullStep<Any?>()
        }
    }
}

fun <E> IProducingStep<E>.firstOrNull(): IMonoStep<E?> {
    return FirstOrNullStep<E>().also { connect(it) }
}
