package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class MapIfNotNullStep<In : Any, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In?, Out?>() {

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createStream(input: StepStream<In?>, context: IStreamInstantiationContext): StepStream<Out?> {
        return input.flatMap { stepOutput ->
            stepOutput.value?.let { query.asStream(context.evaluationContext, stepOutput.upcast()).map { MultiplexedOutput(1, it) } }
                ?: IStream.of(MultiplexedOutput(0, stepOutput.upcast()))
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out?>> {
        val inputSerializer: KSerializer<IStepOutput<In?>> = getProducer().getOutputSerializer(serializationContext).upcast()
        val mappedSerializer: KSerializer<out IStepOutput<Out>> = query.getElementOutputSerializer(
            serializationContext + (query.inputStep to inputSerializer as KSerializer<out IStepOutput<In>>),
        )
        val multiplexedSerializer: MultiplexedOutputSerializer<Out?> = MultiplexedOutputSerializer(
            this,
            listOf(
                inputSerializer.upcast() as KSerializer<IStepOutput<Out?>>,
                mappedSerializer.upcast() as KSerializer<IStepOutput<Out?>>,
            ),
        )
        return multiplexedSerializer
    }

    override fun toString(): String {
        return "${getProducer()}\n.mapIfNotNull {\n${query.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("mapIfNotNull")
    data class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapIfNotNullStep<Any, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }
}

fun <In : Any, Out> IMonoStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}

fun <In : Any, Out> IFluxStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}
