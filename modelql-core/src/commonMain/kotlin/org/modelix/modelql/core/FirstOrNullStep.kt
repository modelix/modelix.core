package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class FirstOrNullStep<RemoteE>() : IConsumingStep<RemoteE>, ITerminalStep<RemoteE?>, ProducingStep<RemoteE?>() {
    private var initialized: Boolean = false
    private var firstElement: RemoteE? = null

    private var producer: IProducingStep<RemoteE>? = null

    override fun addProducer(producer: IProducingStep<RemoteE>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteE, producer: IProducingStep<RemoteE>) {
        if (initialized) return
        initialized = true
        firstElement = element
        forwardToConsumers(element)
        completeConsumers()
    }

    override fun onComplete(producer: IProducingStep<RemoteE>) {
        if (initialized) return
        completeConsumers()
    }

    override fun toString(): String {
        return "$producer.firstOrNull()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*> {
        return (producer!!.getOutputSerializer(serializersModule) as KSerializer<Any>).nullable
    }

    override fun reset() {
        initialized = false
        firstElement = null
    }

    override fun getResult(): RemoteE? {
        return firstElement as RemoteE?
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("firstOrNull")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FirstOrNullStep<Any?>()
        }
    }
}

fun <RemoteOut> IProducingStep<RemoteOut>.firstOrNull(): ITerminalStep<RemoteOut?> {
    return FirstOrNullStep<RemoteOut>().also { connect(it) }
}
