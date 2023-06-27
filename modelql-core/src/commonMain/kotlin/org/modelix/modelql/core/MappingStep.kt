package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class MappingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : TransformingStep<In, Out>() {

    init {
        query.inputStep.indirectConsumer = this
    }
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return query.asFlow(input)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out Out> {
        return query.getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
    }
}

class FluxMappingStep<In, Out>(query: MonoUnboundQuery<In, Out>) : MappingStep<In, Out>(query), IFluxStep<Out> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapMany")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FluxMappingStep<Any?, Any?>(query.createQuery() as MonoUnboundQuery<Any?, Any?>)
        }
    }
}

open class MonoMappingStep<In, Out>(query: MonoUnboundQuery<In, Out>) :
    MappingStep<In, Out>(query), IMonoStep<Out> {

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapSingle")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoMappingStep<Any?, Any?>(query.createQuery() as MonoUnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return FluxMappingStep(IUnboundQuery.buildMono(body).castToInstance()).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MonoMappingStep(IUnboundQuery.buildMono(body).castToInstance()).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.map(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MonoMappingStep(query.castToInstance()).also { connect(it) }
}
