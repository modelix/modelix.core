package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class MappingStep<In, Out>(val query: Query<In, Out>) : TransformingStep<In, Out>() {

    init {
        query.inputStep.indirectConsumer = this
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return query.apply(input, context.coroutineScope)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        return query.getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
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
