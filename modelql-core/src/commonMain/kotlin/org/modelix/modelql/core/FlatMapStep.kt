package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FlatMapStep<In, Out>(val query: FluxUnboundQuery<In, Out>) : TransformingStep<In, Out>(), IFluxStep<Out> {

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        return input.flatMap { query.asStream(context.evaluationContext, it) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return query.outputStep.getOutputSerializer(serializationContext + (query.inputStep to getProducer().getOutputSerializer(serializationContext)))
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("flatMap")
    data class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FlatMapStep<Any?, Any?>(context.getOrCreateQuery(queryId) as FluxUnboundQuery<Any?, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.flatMap {\n${query.toString().prependIndent("  ")}\n}"
    }
}

fun <In, Out> IProducingStep<In>.flatMap(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxStep<Out> {
    return flatMap(buildFluxQuery { body(it) })
}
fun <In, Out> IProducingStep<In>.flatMap(query: IFluxUnboundQuery<In, Out>): IFluxStep<Out> {
    return FlatMapStep(query.castToInstance()).also { connect(it) }
}
