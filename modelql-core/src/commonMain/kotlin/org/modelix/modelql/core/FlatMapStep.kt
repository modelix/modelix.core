package org.modelix.modelql.core

import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FlatMapStep<In, Out>(val query: FluxUnboundQuery<In, Out>) : TransformingStep<In, Out>(), IFluxStep<Out> {
    init {
        query.inputStep.indirectConsumer = this
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMapConcat { query.asFlow(context.evaluationContext, it) }
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Out> {
        return query.asSequence(evaluationContext, getProducer().createSequence(evaluationContext, queryInput))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return query.outputStep.getOutputSerializer(serializersModule)
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor(query.createDescriptor(context))

    @Serializable
    @SerialName("flatMap")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FlatMapStep<Any?, Any?>(query.createQuery(context) as FluxUnboundQuery<Any?, Any?>)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.flatMap { $query }"""
    }
}

fun <In, Out> IProducingStep<In>.flatMap(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxStep<Out> {
    return flatMap(buildFluxQuery { body(it) })
}
fun <In, Out> IProducingStep<In>.flatMap(query: IFluxUnboundQuery<In, Out>): IFluxStep<Out> {
    return FlatMapStep(query.castToInstance()).also { connect(it) }
}
