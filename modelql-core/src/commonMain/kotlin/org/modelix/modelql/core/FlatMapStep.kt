package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FlatMapStep<In, Out>(val query: Query<In, Out>) : TransformingStep<In, Out>(), IFluxStep<Out> {
    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return input.flatMapConcat { query.applyQuery(it) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        return (query.outputStep as IProducingStep<*>).getOutputSerializer(serializersModule) as KSerializer<Out>
    }

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("flatMap")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return FlatMapStep<Any?, Any?>(query.createQuery() as Query<Any?, Any?>)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.flatMap { $query }"""
    }
}

fun <In, Out> IProducingStep<In>.flatMap(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxStep<Out> {
    return FlatMapStep(Query.build(body)).also { connect(it) }
}
