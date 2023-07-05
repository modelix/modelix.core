package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlin.jvm.JvmName

class IfEmptyStep<In : Out, Out>(val alternative: UnboundQuery<Unit, *, Out>) : TransformingStep<In, Out>(), IFluxOrMonoStep<Out> {
    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        val downCastedInput: Flow<Out> = input
        return downCastedInput.onEmpty {
            emitAll(alternative.asFlow(Unit))
        }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        return getProducer().createSequence(queryInput)
            .ifEmpty { alternative.outputStep.createSequence(sequenceOf(Unit)) }
    }

    override fun canBeEmpty(): Boolean = alternative.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || alternative.outputStep.canBeMultiple()
    override fun requiresSingularQueryInput(): Boolean = true

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        TODO("Not yet implemented")
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("ifEmpty")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.ifEmpty { $alternative }"""
    }
}

@JvmName("ifEmpty_mono_mono")
fun <In : Out, Out> IMonoStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IMonoStep<Out> {
    return IfEmptyStep<In, Out>(IUnboundQuery.buildMono<Unit, Out> { alternative() }.castToInstance())
}

@JvmName("ifEmpty_flux_mono")
fun <In : Out, Out> IFluxStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(IUnboundQuery.buildMono<Unit, Out> { alternative() }.castToInstance())
}

@JvmName("ifEmpty_mono_flux")
fun <In : Out, Out> IMonoStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(IUnboundQuery.buildFlux<Unit, Out> { alternative() }.castToInstance())
}

@JvmName("ifEmpty_flux_flux")
fun <In : Out, Out> IFluxStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(IUnboundQuery.buildFlux<Unit, Out> { alternative() }.castToInstance())
}
