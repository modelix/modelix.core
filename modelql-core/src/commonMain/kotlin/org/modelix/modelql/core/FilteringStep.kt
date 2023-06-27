package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
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
        require(!condition.requiresWriteAccess()) { "write access not allowed inside a filtering step: $this" }
        require(!condition.outputStep.canBeMultiple()) {
            "filter condition should return exactly one element, but it can return multiple: $condition"
        }
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E> {
        return input.filter { condition.evaluate(it).presentAndEqual(true) }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return getProducer().createSequence(queryInput).filter { condition.evaluate(it).presentAndEqual(true) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return """${getProducers().single()}.filter { $condition }"""
    }

    override fun createDescriptor() = Descriptor(condition.createDescriptor())

    @Serializable
    @SerialName("filter")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FilteringStep<Any?>(query.createQuery() as MonoUnboundQuery<Any?, Boolean?>)
        }
    }
}

fun <T> IFluxStep<T>.filter(condition: (IMonoStep<T>) -> IMonoStep<Boolean>): IFluxStep<T> {
    return FilteringStep(IUnboundQuery.buildMono { condition(it) }.castToInstance()).also { connect(it) }
}
fun <T> IMonoStep<T>.filter(condition: (IMonoStep<T>) -> IMonoStep<Boolean>): IMonoStep<T> {
    return FilteringStep(IUnboundQuery.buildMono { condition(it) }.castToInstance()).also { connect(it) }
}
