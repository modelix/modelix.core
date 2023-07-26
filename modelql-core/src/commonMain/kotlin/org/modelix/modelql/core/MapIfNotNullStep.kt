package org.modelix.modelql.core

import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        return input.flatMapConcat {
            it.value?.let { query.asFlow(context.evaluationContext, it).map { MultiplexedOutput(1, it) } }
                ?: flowOf(MultiplexedOutput(0, it as IStepOutput<Out?>))
        }
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: In?): Out? {
        return input?.let { query.outputStep.evaluate(evaluationContext, it).getOrElse(null) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out?>> {
        val inputSerializer: KSerializer<out IStepOutput<In?>> = getProducer().getOutputSerializer(serializersModule)
        val mappedSerializer: KSerializer<out IStepOutput<Out>> = query.getElementOutputSerializer(serializersModule)
        val multiplexedSerializer: MultiplexedOutputSerializer<Out?> = MultiplexedOutputSerializer(
            this,
            listOf(
                inputSerializer.upcast() as KSerializer<IStepOutput<Out?>>,
                mappedSerializer.upcast() as KSerializer<IStepOutput<Out?>>
            )
        )
        return multiplexedSerializer
    }

    override fun toString(): String {
        return "${getProducer()}.mapIfNotNull { $query }"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("mapIfNotNull")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapIfNotNullStep<Any, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any, Any?>)
        }
    }
}

fun <In : Any, Out> IMonoStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}

fun <In : Any, Out> IFluxStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}
