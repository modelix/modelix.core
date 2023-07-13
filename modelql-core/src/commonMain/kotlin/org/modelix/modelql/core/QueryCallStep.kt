package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
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
        return getQuery().asFlow(input)
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        val query = getQuery()
        return getProducer().createSequence(queryInput).flatMap { query.asSequence(sequenceOf(it)) }
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun createDescriptor(context: QuerySerializationContext): StepDescriptor {
        return Descriptor(
            queryRef.getId(),
            (queryRef.takeIf { !context.hasQuery(it.getId()) }?.query as UnboundQuery<*, *, *>?)
                ?.createDescriptor(context)
        )
    }

    override fun toString(): String {
        return "${getProducer()}.callQuery(${queryRef.getId()})"
    }

    @Serializable
    @SerialName("queryCall")
    class Descriptor(val queryId: Long, val query: QueryDescriptor? = null) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            val queryRef = QueryReference(
                providedQuery = query?.createQuery(context) as IUnboundQuery<Any?, Any?, Any?>?,
                queryId = queryId,
                queryInitializer = null
            )
            context.register(queryRef)
            return QueryCallStep<Any?, Any?>(queryRef)
        }
    }
}

class RecursiveQuerySerializer<Out>(val query: IUnboundQuery<*, *, Out>) : KSerializer<IStepOutput<Out>> {
    override fun deserialize(decoder: Decoder): IStepOutput<Out> {
        val queryOutputSerializer = query.getElementOutputSerializer(decoder.serializersModule).upcast()
        return queryOutputSerializer.deserialize(decoder)
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("recursiveQuery")

    override fun serialize(encoder: Encoder, value: IStepOutput<Out>) {
        val queryOutputSerializer = query.getElementOutputSerializer(encoder.serializersModule).upcast()
        queryOutputSerializer.serialize(encoder, value)
    }
}

fun <In, Out> buildMonoQuery(body: QueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out>()
    val query = IUnboundQuery.buildMono { body(context, it) }
    context.queryReference.providedQuery = query
    return query
}
fun <In, Out> buildFluxQuery(body: QueryBuilderContext<In, Out>.(IMonoStep<In>) -> IFluxStep<Out>): IFluxUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out>()
    val query = IUnboundQuery.buildFlux { body(context, it) }
    context.queryReference.providedQuery = query
    return query
}

class QueryBuilderContext<In, Out> {
    val queryReference = QueryReference<IUnboundQuery<In, *, Out>>(null, null, null)
    fun IProducingStep<In>.mapRecursive(): IFluxStep<Out> = QueryCallStep<In, Out>(queryReference).also { connect(it) }
}

fun <In, Out> IMonoStep<In>.callQuery(ref: QueryReference<IMonoUnboundQuery<In, Out>>): IMonoStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}

fun <In, Out> IMonoStep<In>.callQuery(ref: QueryReference<IFluxUnboundQuery<In, Out>>): IFluxStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}
