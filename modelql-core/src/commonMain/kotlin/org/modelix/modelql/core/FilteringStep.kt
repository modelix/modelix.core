package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class FilteringStep<E>(val condition: Query<E, Boolean?>) : TransformingStep<E, E>() {

    override fun validate() {
        require(!condition.requiresWriteAccess()) { "write access not allowed inside a filtering step: $this" }
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E> {
        return input.filter { condition.applyQuery(it).singleOrNull() == true }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<E> {
        return getProducer().getOutputSerializer(serializersModule) as KSerializer<E>
    }

    override fun toString(): String {
        return """${getProducers().single()}.filter { $condition }"""
    }
}

class MonoFilteringStep<E>(condition: Query<E, Boolean?>) :
    FilteringStep<E>(condition), IMonoStep<E> {

    override fun createDescriptor() = Descriptor(condition.createDescriptor())

    @Serializable
    @SerialName("filterSingle")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoFilteringStep<Any?>(query.createQuery() as Query<Any?, Boolean?>)
        }
    }
}

class FluxFilteringStep<E>(condition: Query<E, Boolean?>) :
    FilteringStep<E>(condition), IFluxStep<E> {

    override fun createDescriptor() = Descriptor(condition.createDescriptor())

    @Serializable
    @SerialName("filterMany")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoFilteringStep<Any?>(query.createQuery() as Query<Any?, Boolean?>)
        }
    }
}

fun <T> IFluxStep<T>.filter(condition: (IMonoStep<T>) -> IMonoStep<Boolean>): IFluxStep<T> {
    return FluxFilteringStep(Query.build { condition(it).firstOrNull() }).also { connect(it) }
}
fun <T> IMonoStep<T>.filter(condition: (IMonoStep<T>) -> IMonoStep<Boolean>): IMonoStep<T> {
    return MonoFilteringStep(Query.build { condition(it).firstOrNull() }).also { connect(it) }
}
