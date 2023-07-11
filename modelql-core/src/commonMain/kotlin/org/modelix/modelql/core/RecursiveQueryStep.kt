package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.modules.SerializersModule

class RecursiveQueryStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return SERIALIZER
    }

    fun getQuery(): IUnboundQuery<In, *, Out> = owningQuery!! as IUnboundQuery<In, *, Out>

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return (owningQuery!! as IUnboundQuery<In, *, Out>).asFlow(input)
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        val query = getQuery()
        return getProducer().createSequence(queryInput).flatMap { query.asSequence(sequenceOf(it)) }
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("mapRecursive")
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return RecursiveQueryStep<Any?, Any?>()
        }
    }

    companion object {
        val SERIALIZER = NothingSerializer()
    }
}

fun <In, Out> buildMonoQuery(body: QueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out>()
    return IUnboundQuery.buildMono { body(context, it) }
}
fun <In, Out> buildFluxQuery(body: QueryBuilderContext<In, Out>.(IMonoStep<In>) -> IFluxStep<Out>): IFluxUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out>()
    return IUnboundQuery.buildFlux { body(context, it) }
}

class QueryBuilderContext<in In, out Out> {
    fun IProducingStep<In>.mapRecursive(): IFluxStep<Out> = RecursiveQueryStep<In, Out>().also { connect(it) }
}
