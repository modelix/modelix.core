package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class MappingStep<RemoteIn, RemoteOut>(val query: Query<RemoteIn, RemoteOut>) : IConsumingStep<RemoteIn>, ProducingStep<RemoteOut>() {

    init {
        query.inputStep.indirectConsumer = this
    }

    private var producer: IProducingStep<RemoteIn>? = null

    override fun addProducer(producer: IProducingStep<RemoteIn>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteIn, producer: IProducingStep<RemoteIn>) {
        forwardToConsumers(query.run(element))
    }

    override fun onComplete(producer: IProducingStep<RemoteIn>) {
        completeConsumers()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteOut> {
        return query.getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "$producer.map { $query }"
    }
}

class FluxMappingStep<RemoteIn, RemoteOut>(query: Query<RemoteIn, RemoteOut>)
    : MappingStep<RemoteIn, RemoteOut>(query), IFluxStep<RemoteOut> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapMany")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FluxMappingStep<Any?, Any?>(query.createQuery() as Query<Any?, Any?>)
        }
    }
}

class MonoMappingStep<RemoteIn, RemoteOut>(query: Query<RemoteIn, RemoteOut>)
    : MappingStep<RemoteIn, RemoteOut>(query), IMonoStep<RemoteOut> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapSingle")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoMappingStep<Any?, Any?>(query.createQuery() as Query<Any?, Any?>)
        }
    }
}

fun <RemoteIn, RemoteOut> IFluxStep<RemoteIn>.map(body: (IMonoStep<RemoteIn>) -> IMonoStep<RemoteOut>): IFluxStep<RemoteOut> {
    return FluxMappingStep(Query.build(body)).also { connect(it) }
}
fun <RemoteIn, RemoteOut> IMonoStep<RemoteIn>.map(body: (IMonoStep<RemoteIn>) -> IMonoStep<RemoteOut>): IMonoStep<RemoteOut> {
    return MonoMappingStep(Query.build(body)).also { connect(it) }
}
