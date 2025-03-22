package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream
import org.modelix.streams.ifEmpty

class FirstOrNullStep<E>() : AggregationStep<E, E?>() {
    override fun aggregate(input: StepStream<E>, context: IStreamInstantiationContext): IStream.One<IStepOutput<E?>> {
        return input.firstOrEmpty().map { MultiplexedOutput(0, it) }
            .ifEmpty { MultiplexedOutput(1, null.asStepOutput(this)) }
    }

    override fun toString(): String {
        return "${getProducer()}\n.firstOrNull()"
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E?>> {
        return MultiplexedOutputSerializer<E?>(
            this,
            listOf(
                getProducer().getOutputSerializer(serializationContext).upcast(),
                nullSerializer<E>().stepOutputSerializer(this) as KSerializer<IStepOutput<E?>>,
            ),
        )
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("firstOrNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstOrNullStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun <E> IProducingStep<E>.firstOrNull(): IMonoStep<E?> {
    return FirstOrNullStep<E>().also { connect(it) }
}
