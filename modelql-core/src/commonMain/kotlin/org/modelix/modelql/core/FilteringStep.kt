package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

abstract class FilteringStep<RemoteE>(val condition: Query<RemoteE, Boolean?>) : TransformingStep<RemoteE, RemoteE>() {
    override fun transform(element: RemoteE): Sequence<RemoteE> {
        return if (condition.run(element) == true) sequenceOf(element) else emptySequence()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE> {
        return getProducers().single().getOutputSerializer(serializersModule) as KSerializer<RemoteE>
    }

    override fun toString(): String {
        return """${getProducers().single()}.filter { $condition }"""
    }
}

class MonoFilteringStep<RemoteE>(condition: Query<RemoteE, Boolean?>)
    : FilteringStep<RemoteE>(condition), IMonoStep<RemoteE> {

    override fun createDescriptor() = Descriptor(condition.createDescriptor())

    @Serializable
    @SerialName("filterSingle")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoFilteringStep<Any?>(query.createQuery() as Query<Any?, Boolean?>)
        }
    }
}

class FluxFilteringStep<RemoteE>(condition: Query<RemoteE, Boolean?>)
    : FilteringStep<RemoteE>(condition), IFluxStep<RemoteE> {

    override fun createDescriptor() = Descriptor(condition.createDescriptor())

    @Serializable
    @SerialName("filterMany")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MonoFilteringStep<Any?>(query.createQuery() as Query<Any?, Boolean?>)
        }
    }
}

fun <RemoteT> IFluxStep<RemoteT>.filter(condition: (IMonoStep<RemoteT>) -> IMonoStep<Boolean>): IFluxStep<RemoteT> {
    return FluxFilteringStep(Query.build { condition(it).firstOrNull() }).also { connect(it) }
}
fun <RemoteT> IMonoStep<RemoteT>.filter(condition: (IMonoStep<RemoteT>) -> IMonoStep<Boolean>): IMonoStep<RemoteT> {
    return MonoFilteringStep(Query.build { condition(it).firstOrNull() }).also { connect(it) }
}
