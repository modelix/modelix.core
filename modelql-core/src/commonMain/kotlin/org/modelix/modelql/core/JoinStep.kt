package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
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
        override fun createStep(): IStep {
            return JoinStep<Any?>()
        }
    }

    override fun addProducer(producer: IProducingStep<E>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        return producers.map { context.getOrCreateFlow(it) }.asFlow().flattenConcat()
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return producers.asSequence().flatMap { it.createSequence(queryInput) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        val serializers = getProducers().map { it.getOutputSerializer(serializersModule) }.toSet() - RecursiveQueryStep.SERIALIZER
        return when (serializers.size) {
            0 -> throw RuntimeException("No producers found")
            1 -> serializers.first()
            else -> TODO("Different input types not supported yet: $serializers")
        }
    }

    override fun toString(): String {
        return getProducers().joinToString(" + ")
    }
}

operator fun <Common> IProducingStep<Common>.plus(other: IProducingStep<Common>): IFluxStep<Common> = JoinStep<Common>().also {
    it.connect(this)
    it.connect(other)
}
