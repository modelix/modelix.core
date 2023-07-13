package org.modelix.modelql.core

import kotlinx.coroutines.flow.count
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class CountingStep() : AggregationStep<Any?, Int>() {
    override suspend fun aggregate(input: StepFlow<Any?>): IStepOutput<Int> {
        return input.count().asStepOutput()
    }

    override fun aggregate(input: Sequence<IStepOutput<Any?>>): IStepOutput<Int> = input.count().asStepOutput()

    override fun createDescriptor(context: QuerySerializationContext) = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CountingStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer()
    }

    override fun toString(): String {
        return "${getProducers().single()}.count()"
    }
}

fun IProducingStep<Any?>.size() = count()
fun IProducingStep<Any?>.count() = CountingStep().connectAndDowncast(this)
