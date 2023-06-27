package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class CountingStep() : AggregationStep<Any?, Int>() {
    override suspend fun aggregate(input: Flow<Any?>): Int {
        return input.count()
    }

    override fun aggregate(input: Sequence<Any?>): Int = input.count()

    override fun createDescriptor() = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return CountingStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Int> {
        return serializersModule.serializer<Int>()
    }

    override fun toString(): String {
        return "${getProducers().single()}.count()"
    }
}

fun IProducingStep<Any?>.size() = count()
fun IProducingStep<Any?>.count() = CountingStep().connectAndDowncast(this)
