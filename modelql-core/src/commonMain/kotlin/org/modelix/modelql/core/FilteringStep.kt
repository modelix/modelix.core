package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FilteringStep<E>(val condition: MonoUnboundQuery<E, Boolean?>) : TransformingStep<E, E>(), IMonoStep<E>, IFluxStep<E> {

    override fun canBeEmpty(): Boolean = true

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    override fun validate() {
        super<TransformingStep>.validate()
        require(!condition.requiresWriteAccess()) { "write access not allowed inside a filtering step: $this" }
        require(!condition.outputStep.canBeMultiple()) {
            "filter condition should not return multiple elements: $condition"
        }
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.filterBySingle { condition.asStream(context.evaluationContext, it).map { it.value == true }.firstOrDefault(false) }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.filter {\n${condition.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(condition))

    @Serializable
    @SerialName("filter")
    data class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FilteringStep<Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Boolean?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }
}

fun <T> IFluxStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IFluxStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
fun <T> IMonoStep<T>.filter(condition: IQueryBuilderContext<T, Boolean>.(IMonoStep<T>) -> IMonoStep<Boolean>): IMonoStep<T> {
    return FilteringStep(buildMonoQuery(condition).castToInstance()).also { connect(it) }
}
