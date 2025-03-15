package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class IntSumAggregationStep : AggregationStep<Int, Int>() {
    override fun aggregate(input: StepStream<Int>, context: IStreamInstantiationContext): IStream.One<IStepOutput<Int>> {
        return input.fold(0) { acc, it -> acc + it.value }.asStepStream(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("intSumAggregation")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IntSumAggregationStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Int>> {
        return serializationContext.serializer<Int>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducer()}\n.sum()"
    }
}

fun IFluxStep<Int>.sum(): IMonoStep<Int> = IntSumAggregationStep().also { connect(it) }
fun IMonoStep<Int>.sum(other: IMonoStep<Int>): IMonoStep<Int> = this.plus(other).sum()
