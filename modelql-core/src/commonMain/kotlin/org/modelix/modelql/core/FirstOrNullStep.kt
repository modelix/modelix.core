package org.modelix.modelql.core

import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.observable.firstOrComplete
import com.badoo.reaktive.single.Single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FirstOrNullStep<E>() : AggregationStep<E, E?>() {
    override fun aggregate(input: StepStream<E>, context: IStreamInstantiationContext): Single<IStepOutput<E?>> {
        return input.firstOrComplete().map { MultiplexedOutput(0, it) }
            .defaultIfEmpty(MultiplexedOutput(1, null.asStepOutput(this)))
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
