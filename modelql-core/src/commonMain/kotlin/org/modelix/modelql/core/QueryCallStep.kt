package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class QueryCallStep<In, Out>(val queryRef: QueryReference<out IUnboundQuery<In, *, Out>>) : TransformingStep<In, Out>(), IFluxStep<Out>, IMonoStep<Out> {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return RecursiveQuerySerializer<Out>(getQuery(), this, serializationContext)
    }

    fun getQuery(): IUnboundQuery<In, *, Out> = queryRef.query!!

    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        return getQuery().asStream(context.evaluationContext, input)
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(context.load(queryRef.query))
    }

    override fun toString(): String {
        return "${getProducer()}\n.callQuery(${queryRef.getId()})"
    }

    @Serializable
    @SerialName("queryCall")
    data class Descriptor(val queryId: QueryId) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return QueryCallStep<Any?, Any?>(context.getOrCreateQueryReference(queryId) as QueryReference<IUnboundQuery<Any?, *, Any?>>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }
}

class RecursiveQuerySerializer<Out>(
    val query: IUnboundQuery<*, *, Out>,
    val owner: QueryCallStep<*, Out>,
    val serializationContext: SerializationContext,
) : KSerializer<IStepOutput<Out>> {
    override fun deserialize(decoder: Decoder): IStepOutput<Out> {
        val queryOutputSerializer = getQueryOutputSerializer()
        return queryOutputSerializer.deserialize(decoder)
    }

    override val descriptor: SerialDescriptor = NothingSerializer().descriptor

    override fun serialize(encoder: Encoder, value: IStepOutput<Out>) {
        val queryOutputSerializer = getQueryOutputSerializer()
        queryOutputSerializer.serialize(encoder, value)
    }

    private fun getQueryOutputSerializer(): KSerializer<IStepOutput<Out>> {
        return query.getElementOutputSerializer(
            serializationContext + (query.castToInstance().inputStep to owner.getProducer().getOutputSerializer(serializationContext) as KSerializer<out IStepOutput<Nothing>>),
        ).upcast()
    }
}

fun <In, Out> IMonoStep<In>.callQuery(ref: QueryReference<IMonoUnboundQuery<In, Out>>): IMonoStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}
fun <In, Out> IMonoStep<In>.callQuery(query: () -> IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    val creationStacktrace = Exception()
    return callQuery(
        QueryReference(null, null) {
            val q: IMonoUnboundQuery<In, Out>? = query()
            q ?: throw RuntimeException("No query was provided. Possible cyclic dependency.", creationStacktrace)
        },
    )
}
fun <In, Out> IMonoStep<In>.callFluxQuery(query: () -> IFluxUnboundQuery<In, Out>): IFluxStep<Out> {
    val creationStacktrace = Exception()
    return callFluxQuery(
        QueryReference(null, null) {
            val q: IFluxUnboundQuery<In, Out>? = query()
            q ?: throw RuntimeException("No query was provided. Possible cyclic dependency.", creationStacktrace)
        },
    )
}

fun <In, Out> IMonoStep<In>.callFluxQuery(ref: QueryReference<IFluxUnboundQuery<In, Out>>): IFluxStep<Out> {
    return QueryCallStep<In, Out>(ref).also { connect(it) }
}
