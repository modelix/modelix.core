package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class SingleStep<E>() : AggregationStep<E, E>() {

    override fun aggregate(input: StepStream<E>, context: IStreamInstantiationContext): IStream.One<IStepOutput<E>> {
        return input.exactlyOne()
    }

    override fun toString(): String {
        return "${getProducer()}\n.single()"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("single")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SingleStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun <E> IFluxStep<E>.single(): IMonoStep<E> {
    return SingleStep<E>().also { connect(it) }
}
