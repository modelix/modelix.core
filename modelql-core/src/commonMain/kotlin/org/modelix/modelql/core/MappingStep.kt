package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class MappingStep<In, Out>(val query: UnboundQuery<In, Out>) : TransformingStep<In, Out>() {

    init {
        query.inputStep.indirectConsumer = this
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return query.applyQuery(input)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        return query.getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
    }
}

class FluxMappingStep<In, Out>(query: UnboundQuery<In, Out>) :
    MappingStep<In, Out>(query), IFluxStep<Out> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapMany")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FluxMappingStep<Any?, Any?>(query.createQuery() as UnboundQuery<Any?, Any?>)
        }
    }
}

class MonoMappingStep<In, Out>(query: UnboundQuery<In, Out>) :
    MappingStep<In, Out>(query), IMonoStep<Out> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapSingle")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoMappingStep<Any?, Any?>(query.createQuery() as UnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return FluxMappingStep(UnboundQuery.build(body)).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MonoMappingStep(UnboundQuery.build(body)).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.map(query: IUnboundQuery<In, Out>): IFluxStep<Out> {
    return FluxMappingStep(query as UnboundQuery<In, Out>).also { connect(it) }
}
fun <In, Out> IFluxStep<In>.map(query: IUnboundQuery<In, Out>): IFluxStep<Out> {
    return FluxMappingStep(query as UnboundQuery<In, Out>).also { connect(it) }
}
