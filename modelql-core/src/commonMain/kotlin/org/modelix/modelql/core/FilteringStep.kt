package org.modelix.modelql.core

import kotlinx.coroutines.flow.filter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FilteringStep<E>(val condition: MonoUnboundQuery<E, Boolean?>) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    init {
        condition.inputStep.indirectConsumer = this
    }

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun validate() {
        super<TransformingStep>.validate()
        require(!condition.requiresWriteAccess()) { "write access not allowed inside a filtering step: $this" }
        require(!condition.outputStep.canBeMultiple()) {
            "filter condition should not return multiple elements: $condition"
        }
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        // return condition.asFlow(input).zip(input) { c, it -> c to it }.filter { it.first == true }.map { it.second }
        return input.filter { condition.asFlow(context.evaluationContext, it).value.optionalSingle().presentAndEqual(true) }
        // return input.filter { condition.evaluate(it.value).presentAndEqual(true) }
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<E> {
        return getProducer().createSequence(evaluationContext, queryInput).filter {
            condition.evaluate(
                evaluationContext,
                it
            ).presentAndEqual(true)
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return """${getProducers().single()}.filter { $condition }"""
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(condition))

    @Serializable
    @SerialName("filter")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FilteringStep<Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Boolean?>)
        }
    }
}

fun <T> IFluxStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IFluxStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
fun <T> IMonoStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IMonoStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
