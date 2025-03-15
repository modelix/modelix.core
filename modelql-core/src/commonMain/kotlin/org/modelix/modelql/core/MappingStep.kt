package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream

class MappingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In, Out>() {

    override fun validate() {
        super.validate()
        check(query.outputStep != this)
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresSingularQueryInput(): Boolean {
        return super.requiresSingularQueryInput() || query.inputStep.requiresSingularQueryInput()
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        return input.flatMap { query.asStream(context.evaluationContext, IStream.of(it)) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return query.getAggregationOutputSerializer(serializationContext + (query.inputStep to getProducer().getOutputSerializer(serializationContext)))
    }

    override fun toString(): String {
        return "${getProducer()}\n.map {\n${query.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("map")
    data class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MappingStep<Any?, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: IStepSharingContext.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(body: IStepSharingContext.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IFluxStep<In>.map(query: IMonoUnboundQuery<In, Out>): IFluxStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
