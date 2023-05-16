package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class IfEmptyStep<RemoteIn : RemoteOut, RemoteOut>(val alternative: Query<Unit, List<RemoteOut>>) : IConsumingStep<RemoteIn>, ProducingStep<RemoteOut>(), IFluxStep<RemoteOut> {
    private var completed: Boolean = false
    private var anyElementEmitted: Boolean = false

    private var producer: IProducingStep<RemoteIn>? = null

    override fun addProducer(producer: IProducingStep<RemoteIn>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteIn, producer: IProducingStep<RemoteIn>) {
        anyElementEmitted = true
        forwardToConsumers(element)
    }

    override fun onComplete(producer: IProducingStep<RemoteIn>) {
        completed = true
        if (!anyElementEmitted) {
            // TODO forward elements directly without collecting them in a list
            val alternativeElements: List<RemoteOut> = alternative.run(Unit)
            alternativeElements.forEach { forwardToConsumers(it) }
        }
        completeConsumers()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteOut> {
        TODO("Not yet implemented")
    }

    override fun reset() {
        completed = false
        anyElementEmitted = false
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("ifEmpty")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.ifEmpty { $alternative }"""
    }
}

fun <RemoteIn : RemoteOut, RemoteOut> IMonoStep<RemoteIn>.ifEmpty(alternative: () -> IMonoStep<RemoteOut>): IMonoStep<RemoteOut> {
    return IfEmptyStep<RemoteIn, RemoteOut>(Query.build { alternative().toSingletonList() }).first()
}

fun <RemoteIn : RemoteOut, RemoteOut> IFluxStep<RemoteIn>.ifEmpty(alternative: () -> IMonoStep<RemoteOut>): IFluxStep<RemoteOut> {
    return IfEmptyStep<RemoteIn, RemoteOut>(Query.build { alternative().toSingletonList() })
}

fun <RemoteIn : RemoteOut, RemoteOut> IProducingStep<RemoteIn>.ifEmpty(alternative: () -> IFluxStep<RemoteOut>): IFluxStep<RemoteOut> {
    return IfEmptyStep<RemoteIn, RemoteOut>(Query.build { alternative().toList() })
}
