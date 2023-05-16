package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class NullIfEmpty<RemoteE>() : IConsumingStep<RemoteE>, ProducingStep<RemoteE?>(), IMonoStep<RemoteE?> {
    private var completed: Boolean = false
    private var anyElementEmitted: Boolean = false

    private var producer: IProducingStep<RemoteE>? = null

    override fun addProducer(producer: IProducingStep<RemoteE>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteE, producer: IProducingStep<RemoteE>) {
        anyElementEmitted = true
        forwardToConsumers(element)
    }

    override fun onComplete(producer: IProducingStep<RemoteE>) {
        completed = true
        if (!anyElementEmitted) {
            forwardToConsumers(null)
        }
        completeConsumers()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE?> {
        val outputSerializer = producer!!.getOutputSerializer(serializersModule) as KSerializer<Any>
        return outputSerializer.nullable as KSerializer<RemoteE?>
    }

    override fun reset() {
        completed = false
        anyElementEmitted = false
    }

    override fun createDescriptor() = OrNullDescriptor()

    @Serializable
    @SerialName("orNull")
    class OrNullDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.orNull()"""
    }
}

fun <RemoteOut> IMonoStep<RemoteOut>.orNull(): IMonoStep<RemoteOut?> = NullIfEmpty<RemoteOut>().also { connect(it) }