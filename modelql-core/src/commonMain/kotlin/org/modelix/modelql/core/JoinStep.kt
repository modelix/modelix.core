package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream
import org.modelix.streams.flatten

class JoinStep<E>() : ProducingStep<E>(), IConsumingStep<E>, IFluxStep<E> {
    override fun canBeEmpty(): Boolean = getProducers().all { it.canBeEmpty() }
    override fun canBeMultiple(): Boolean = true
    override fun requiresSingularQueryInput(): Boolean = true

    private val producers = ArrayList<IProducingStep<E>>()

    override fun getProducers(): List<IProducingStep<E>> {
        return producers
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("join")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return JoinStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun addProducer(producer: IProducingStep<E>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun createStream(context: IStreamInstantiationContext): StepStream<E> {
        return IStream.many(producers.mapIndexed { prodIndex, it -> context.getOrCreateStream(it).map { MultiplexedOutput(prodIndex, it) } }).flatten()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return MultiplexedOutputSerializer(this, getProducers().map { it.getOutputSerializer(serializationContext).upcast() })
    }

    override fun toString(): String {
        return "join(\n${getProducers().joinToString("\n,\n") { it.toString().prependIndent("  ") }}\n)"
    }
}

operator fun <Common> IProducingStep<Common>.plus(other: IProducingStep<Common>): IFluxStep<Common> = JoinStep<Common>().also {
    it.connect(this)
    it.connect(other)
}
