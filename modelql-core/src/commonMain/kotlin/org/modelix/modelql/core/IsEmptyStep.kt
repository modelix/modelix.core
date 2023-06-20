package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsEmptyStep() : AggregationStep<Any?, Boolean>() {
    override suspend fun aggregate(input: Flow<Any?>): Boolean {
        return input.take(1).map { false }.onEmpty { emit(false) }.single()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("isEmpty")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IsEmptyStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun toString(): String {
        return "${getProducers().single()}.isEmpty()"
    }
}

fun IProducingStep<Any?>.isEmpty(): IMonoStep<Boolean> = IsEmptyStep().connectAndDowncast(this)
fun IProducingStep<Any?>.isNotEmpty(): IMonoStep<Boolean> = !isEmpty()
