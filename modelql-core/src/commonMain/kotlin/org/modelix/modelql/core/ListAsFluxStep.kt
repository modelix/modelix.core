package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class ListAsFluxStep<E> : FluxTransformingStep<List<E>, E>() {
    override fun createStream(input: StepStream<List<E>>, context: IStreamInstantiationContext): StepStream<E> {
        return input.flatMap {
            when (it) {
                is CollectorStepOutput<*, *, *> -> {
                    IStream.many((it as CollectorStepOutput<E, List<IStepOutput<E>>, List<E>>).input)
                }
                else -> IStream.many(it.value).map {
                    when (it) {
                        is IStepOutput<*> -> it as IStepOutput<E>
                        else -> it.asStepOutput(this)
                    }
                }
            }
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        val inputSerializer = getProducer().getOutputSerializer(serializationContext).let {
            when (it) {
                is MultiplexedOutputSerializer<*> -> it.serializers.first()
                else -> it
            }
        }
        return when (inputSerializer) {
            is ListCollectorStepOutputSerializer<*> -> (inputSerializer as ListCollectorStepOutputSerializer<E>).inputElementSerializer
            else -> throw UnsupportedOperationException("Cannot serialize elements of ${getProducer()}")
        }
    }

    override fun toString(): String {
        return "${getProducer()}\n.toFlux()"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("listToFlux")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ListAsFluxStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun <T> IMonoStep<List<T>>.toFlux(): IFluxStep<T> = ListAsFluxStep<T>().also { connect(it) }
