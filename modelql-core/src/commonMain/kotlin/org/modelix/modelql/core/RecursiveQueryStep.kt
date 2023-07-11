package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

class RecursiveQueryStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return RecursiveQuerySerializer<Out>(getQuery())
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

    override fun toString(): String {
        return "${getProducer()}.mapRecursive()"
    }

    @Serializable
    @SerialName("mapRecursive")
    class Descriptor : StepDescriptor() {
        override fun createStep(): IStep {
            return RecursiveQueryStep<Any?, Any?>()
        }
    }
}

class RecursiveQuerySerializer<Out>(val query: IUnboundQuery<*, *, Out>) : KSerializer<IStepOutput<Out>> {
    override fun deserialize(decoder: Decoder): IStepOutput<Out> {
        val queryOutputSerializer = query.getElementOutputSerializer(decoder.serializersModule).upcast()
        return queryOutputSerializer.deserialize(decoder)
    }

    override val descriptor: SerialDescriptor
        get() = TODO("Not yet implemented")

    override fun serialize(encoder: Encoder, value: IStepOutput<Out>) {
        val queryOutputSerializer = query.getElementOutputSerializer(encoder.serializersModule).upcast()
        queryOutputSerializer.serialize(encoder, value)
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
