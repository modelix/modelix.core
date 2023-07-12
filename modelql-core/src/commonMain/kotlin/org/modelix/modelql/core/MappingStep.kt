package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MappingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In, Out>() {

    init {
        query.inputStep.indirectConsumer = this
    }
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresSingularQueryInput(): Boolean {
        return super.requiresSingularQueryInput() || query.inputStep.requiresSingularQueryInput()
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return query.asFlow(input)
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        return query.asSequence(queryInput as Sequence<In>)
    }

    override fun evaluate(queryInput: Any?): Optional<Out> {
        return getProducer().evaluate(queryInput).flatMap { query.evaluate(it) }
    }

    override fun transform(input: In): Out {
        return query.evaluate(input).get()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return query.getAggregationOutputSerializer(serializersModule)
    }

    override fun toString(): String {
        return "${getProducer()}.map { $query }"
    }

    override fun createDescriptor() = Descriptor(query.createDescriptor())

    @Serializable
    @SerialName("map")
    class Descriptor(val query: QueryDescriptor) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return MappingStep<Any?, Any?>(query.createQuery() as MonoUnboundQuery<Any?, Any?>)
        }
    }
}

fun <In, Out> IFluxStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return MappingStep(IUnboundQuery.buildMono(body).castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MappingStep(IUnboundQuery.buildMono(body).castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.map(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IFluxStep<In>.map(query: IMonoUnboundQuery<In, Out>): IFluxStep<Out> {
    return MappingStep(query.castToInstance()).connectAndDowncast(this)
}
