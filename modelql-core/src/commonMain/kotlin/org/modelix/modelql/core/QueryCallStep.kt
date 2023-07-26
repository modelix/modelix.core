package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

class QueryCallStep<In, Out>(val queryRef: QueryReference<out IUnboundQuery<In, *, Out>>) : TransformingStep<In, Out>(), IFluxStep<Out>, IMonoStep<Out> {
    override fun validate() {
        super<TransformingStep>.validate()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return RecursiveQuerySerializer<Out>(getQuery())
    }

    fun getQuery(): IUnboundQuery<In, *, Out> = queryRef.query!!

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return getQuery().asFlow(context.evaluationContext, input)
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Out> {
        val query = getQuery()
        return getProducer().createSequence(evaluationContext, queryInput).flatMap { query.asSequence(evaluationContext, sequenceOf(it)) }
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(context.load(queryRef.query))
    }

    override fun toString(): String {
        return "${getProducer()}.callQuery(${queryRef.getId()})"
    }

    @Serializable
    @SerialName("queryCall")
    class Descriptor(val queryId: QueryId) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return QueryCallStep<Any?, Any?>(context.getOrCreateQueryReference(queryId) as QueryReference<IUnboundQuery<Any?, *, Any?>>)
        }
    }
}

class RecursiveQuerySerializer<Out>(val query: IUnboundQuery<*, *, Out>) : KSerializer<IStepOutput<Out>> {
    override fun deserialize(decoder: Decoder): IStepOutput<Out> {
        val queryOutputSerializer = getQueryOutputSerializer(decoder.serializersModule)
        return queryOutputSerializer.deserialize(decoder)
    }

    override val descriptor: SerialDescriptor = NothingSerializer().descriptor

    override fun serialize(encoder: Encoder, value: IStepOutput<Out>) {
        val queryOutputSerializer = getQueryOutputSerializer(encoder.serializersModule)
        queryOutputSerializer.serialize(encoder, value)
    }

    private fun getQueryOutputSerializer(serializersModule: SerializersModule) =
        query.getElementOutputSerializer(serializersModule).upcast()
}

fun <In, Out> IMonoStep<In>.callQuery(ref: QueryReference<IMonoUnboundQuery<In, Out>>): IMonoStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.callQuery(query: () -> IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return callQuery(QueryReference(null, null, query))
}
fun <In, Out> IMonoStep<In>.callFluxQuery(query: () -> IFluxUnboundQuery<In, Out>): IFluxStep<Out> {
    return callFluxQuery(QueryReference(null, null, query))
}

fun <In, Out> IMonoStep<In>.callFluxQuery(ref: QueryReference<IFluxUnboundQuery<In, Out>>): IFluxStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}
