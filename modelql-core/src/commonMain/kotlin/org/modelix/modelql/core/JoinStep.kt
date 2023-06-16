package org.modelix.modelql.core

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.concatMap
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class JoinStep<E>() : ProducingStep<E>(), IConsumingStep<E>, IFluxStep<E> {
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

    @OptIn(FlowPreview::class)
    override fun createFlow(context: IFlowInstantiationContext): Flow<E> {
        return producers.map { context.getOrCreateFlow(it) }.asFlow().flattenConcat()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<E> {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return getProducers().joinToString(" + ")
    }
}

operator fun <RemoteCommon> IProducingStep<RemoteCommon>.plus(other: IProducingStep<RemoteCommon>): IFluxStep<RemoteCommon> = JoinStep<RemoteCommon>().also {
    it.connect(this)
    it.connect(other)
}