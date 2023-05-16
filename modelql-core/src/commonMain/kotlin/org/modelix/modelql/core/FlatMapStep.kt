package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FlatMapStep<RemoteIn, RemoteOut>(val query: Query<RemoteIn, List<RemoteOut>>) : IConsumingStep<RemoteIn>, ProducingStep<RemoteOut>(),
    IFluxStep<RemoteOut> {

    private var producer: IProducingStep<RemoteIn>? = null

    override fun addProducer(producer: IProducingStep<RemoteIn>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteIn, producer: IProducingStep<RemoteIn>) {
        // TODO forward elements to output directly without building a list
        query.run(element).forEach { forwardToConsumers(it) }
    }

    override fun onComplete(producer: IProducingStep<RemoteIn>) {
        completeConsumers()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteOut> {
        return (query.outputStep as ListCollectorStep).getProducers().first().getOutputSerializer(serializersModule) as KSerializer<RemoteOut>
    }

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("flatMap")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FlatMapStep<Any?, Any?>(query.createQuery() as Query<Any?, List<Any?>>)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.flatMap { $query }"""
    }
}


fun <RemoteIn, RemoteOut> IProducingStep<RemoteIn>.flatMap(body: (IMonoStep<RemoteIn>) -> IFluxStep<RemoteOut>): IFluxStep<RemoteOut> {
    return FlatMapStep(Query.build { body(it).toList() }).also { connect(it) }
}
