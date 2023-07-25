package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MappingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In, Out>() {

    init {
        query.inputStep.indirectConsumer = this
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

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return query.asFlow(context.evaluationContext, input)
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Out> {
        return query.asSequence(evaluationContext, queryInput as Sequence<In>)
    }

    override fun evaluate(evaluationContext: QueryEvaluationContext, queryInput: Any?): Optional<Out> {
        return getProducer().evaluate(evaluationContext, queryInput).flatMap { query.evaluate(evaluationContext, it) }
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: In): Out {
        return query.evaluate(evaluationContext, input).get()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return query.getAggregationOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("map")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MappingStep<Any?, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: IMappingContext.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(body: IMappingContext.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MappingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IFluxStep<In>.map(query: IMonoUnboundQuery<In, Out>): IFluxStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
