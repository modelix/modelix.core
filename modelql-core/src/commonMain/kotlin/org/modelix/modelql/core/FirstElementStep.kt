package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FirstElementStep<RemoteE>() : IConsumingStep<RemoteE>, ITerminalStep<RemoteE>, ProducingStep<RemoteE>() {
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

    override fun onNext(element: RemoteE, source: IProducingStep<RemoteE>) {
        if (initialized) return
        initialized = true
        firstElement = element
        forwardToConsumers(element)
        completeConsumers()
    }

    override fun onComplete(source: IProducingStep<RemoteE>) {
        if (initialized) return
        completeConsumers()
    }

    override fun toString(): String {
        return producer.toString() + ".first()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*> {
        return producer!!.getOutputSerializer(serializersModule)
    }

    override fun reset() {
        initialized = false
        firstElement = null
    }

    override fun getResult(): RemoteE {
        if (!initialized) throw IllegalStateException("No element received")
        return firstElement as RemoteE
    }

    override fun createDescriptor() = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FirstElementStep<Any?>()
        }
    }
}

fun <RemoteOut> IFluxStep<RemoteOut>.first(): IMonoStep<RemoteOut> {
    return FirstElementStep<RemoteOut>().also { it.connect(this) }
}

fun <RemoteOut> IMonoStep<RemoteOut>.toTerminal(): ITerminalStep<RemoteOut> {
    if (this is ITerminalStep) return this
    return FirstElementStep<RemoteOut>().also { it.connect(this) }
}
