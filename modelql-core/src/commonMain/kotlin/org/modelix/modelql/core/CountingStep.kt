package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class CountingStep() : AggregationStep<Any?, Int>() {
    override fun aggregate(input: StepStream<Any?>, context: IStreamInstantiationContext): IStream.One<IStepOutput<Int>> {
        return input.count().asStepStream(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = CountDescriptor()

    @Serializable
    @SerialName("count")
    class CountDescriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return CountingStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = CountDescriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.count()"
    }
}

fun IProducingStep<*>.count(): IMonoStep<Int> = CountingStep().also { connect(it) }
