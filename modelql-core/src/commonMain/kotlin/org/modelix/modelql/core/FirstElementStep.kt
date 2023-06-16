package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FirstElementStep<E>() : AggregationStep<E, E>() {
    override suspend fun aggregate(input: Flow<E>): E {
        return input.first()
    }

    override fun toString(): String {
        return getProducer().toString() + ".first()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createDescriptor() = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FirstElementStep<Any?>()
        }
    }
}

fun <RemoteOut> IProducingStep<RemoteOut>.first(): IMonoStep<RemoteOut> {
    return FirstElementStep<RemoteOut>().also { it.connect(this) }
}
