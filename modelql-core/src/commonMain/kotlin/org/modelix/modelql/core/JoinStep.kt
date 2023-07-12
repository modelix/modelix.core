package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class JoinStep<E>() : ProducingStep<E>(), IConsumingStep<E>, IFluxStep<E> {
    override fun canBeEmpty(): Boolean = getProducers().all { it.canBeEmpty() }
    override fun canBeMultiple(): Boolean = true
    override fun requiresSingularQueryInput(): Boolean = true

    private val producers = ArrayList<IProducingStep<E>>()

    override fun getProducers(): List<IProducingStep<E>> {
        return producers
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("join")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return JoinStep<Any?>()
        }
    }

    override fun addProducer(producer: IProducingStep<E>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        return producers.mapIndexed { prodIndex, it -> context.getOrCreateFlow(it).map { MultiplexedOutput(prodIndex, it) } }
            .asFlow().flattenConcat()
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return producers.asSequence().flatMap { it.createSequence(queryInput) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return MultiplexedOutputSerializer(getProducers().map { it.getOutputSerializer(serializersModule).upcast() })
    }

    override fun toString(): String {
        return getProducers().joinToString(" + ")
    }
}

operator fun <Common> IProducingStep<Common>.plus(other: IProducingStep<Common>): IFluxStep<Common> = JoinStep<Common>().also {
    it.connect(this)
    it.connect(other)
}
