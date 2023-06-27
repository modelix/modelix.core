package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class FirstOrNullStep<E>() : AggregationStep<E, E?>() {

    override suspend fun aggregate(input: Flow<E>): E? {
        return input.firstOrNull()
    }

    override fun aggregate(input: Sequence<E>): E? {
        return input.firstOrNull()
    }

    override fun toString(): String {
        return "${getProducer()}.firstOrNull()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<E?> {
        return (getProducer().getOutputSerializer(serializersModule) as KSerializer<Any>).nullable as KSerializer<E?>
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("firstOrNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FirstOrNullStep<Any?>()
        }
    }
}

fun <E> IProducingStep<E>.firstOrNull(): IMonoStep<E?> {
    return FirstOrNullStep<E>().also { connect(it) }
}
