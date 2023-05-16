package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class JoinStep<RemoteE>() : ProducingStep<RemoteE>(), IConsumingStep<RemoteE>, IFluxStep<RemoteE> {
    private val inputPorts = ArrayList<JoinInputPort>()
    private var stepCompleted = false

    override fun getProducers(): List<IProducingStep<*>> {
        return inputPorts.map { it.producer }
    }

    override fun onNext(element: RemoteE, producer: IProducingStep<RemoteE>) {
        inputPorts.first { it.producer == producer }.onNext(element)
    }

    override fun onComplete(producer: IProducingStep<RemoteE>) {
        inputPorts.first { it.producer == producer }.onComplete()
    }

    override fun reset() {
        stepCompleted = false
        inputPorts.forEach { it.reset() }
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("join")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return JoinStep<Any?>()
        }
    }

    inner class JoinInputPort(val producer: IProducingStep<RemoteE>) {
        private var portCompleted = false

        fun reset() {
            portCompleted = false
        }

        fun onNext(element: RemoteE) {
            forwardToConsumers(element)
        }

        fun onComplete() {
            portCompleted = true
            if (!stepCompleted) {
                if (inputPorts.all { it.portCompleted }) {
                    stepCompleted = true
                    completeConsumers()
                }
            }
        }
    }

    override fun addProducer(producer: IProducingStep<RemoteE>) {
        if (getProducers().contains(producer)) return
        inputPorts.add(JoinInputPort(producer))
        producer.addConsumer(this)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE> {
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