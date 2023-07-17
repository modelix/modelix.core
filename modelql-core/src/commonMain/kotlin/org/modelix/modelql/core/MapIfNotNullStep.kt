package org.modelix.modelql.core

import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MapIfNotNullStep<In : Any, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In?, Out?>() {

    init {
        query.inputStep.indirectConsumer = this
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In?>, context: IFlowInstantiationContext): StepFlow<Out?> {
        return input.flatMapConcat { it.value?.let { query.asFlow(it) } ?: flowOf(null).asStepFlow() }
    }

    override fun transform(input: In?): Out? {
        return input?.let { query.outputStep.evaluate(it).getOrElse(null) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out?>> {
        return query.getAggregationOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.mapIfNotNull { $query }"
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor(query.createDescriptor(context))

    @Serializable
    @SerialName("mapIfNotNull")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapIfNotNullStep<Any, Any?>(query.createQuery(context) as MonoUnboundQuery<Any, Any?>)
        }
    }
}

fun <In : Any, Out> IMonoStep<In?>.mapIfNotNull(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out?> {
    return MapIfNotNullStep(IUnboundQuery.buildMono(body).castToInstance()).also { connect(it) }
}

fun <In : Any, Out> IFluxStep<In?>.mapIfNotNull(body: (IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out?> {
    return MapIfNotNullStep(IUnboundQuery.buildMono(body).castToInstance()).also { connect(it) }
}
