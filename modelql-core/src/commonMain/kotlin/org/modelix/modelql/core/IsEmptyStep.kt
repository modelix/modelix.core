package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class IsEmptyStep() : AggregationStep<Any?, Boolean>() {
    override fun aggregate(input: StepStream<Any?>, context: IStreamInstantiationContext): IStream.One<IStepOutput<Boolean>> {
        return input.isEmpty().asStepStream(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("isEmpty")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IsEmptyStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.isEmpty()"
    }
}

fun IProducingStep<Any?>.isEmpty(): IMonoStep<Boolean> = IsEmptyStep().connectAndDowncast(this)
fun IProducingStep<Any?>.isNotEmpty(): IMonoStep<Boolean> = !isEmpty()
