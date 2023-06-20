package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MapIfNotNullStep<In, Out>(val query: UnboundQuery<In, Out>) : MonoTransformingStep<In?, Out?>() {

    init {
        query.inputStep.indirectConsumer = this
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: Flow<In?>, context: IFlowInstantiationContext): Flow<Out?> {
        return input.flatMapConcat { it?.let { query.applyQuery(it) } ?: flowOf(null) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        return query.getOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.mapIfNotNull { $query }"
    }

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("mapIfNotNull")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MapIfNotNullStep<Any?, Any?>(query.createQuery() as UnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IMonoStep<In?>.mapIfNotNull(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out?> {
    return MapIfNotNullStep(UnboundQuery.build(body)).also { connect(it) }
}
