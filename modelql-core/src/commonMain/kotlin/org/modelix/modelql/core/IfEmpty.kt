package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.switchIfEmpty
import kotlin.jvm.JvmName

class IfEmptyStep<In : Out, Out>(val alternative: UnboundQuery<Unit, *, Out>) : TransformingStep<In, Out>(), IFluxOrMonoStep<Out> {
    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        val downCastedInput: StepStream<Out> = input
        return downCastedInput.map { MultiplexedOutput(0, it) }.switchIfEmpty {
            alternative.asStream(context.evaluationContext, Unit.asStepOutput(null)).map { MultiplexedOutput(1, it) }
        }
    }

    override fun canBeEmpty(): Boolean = alternative.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || alternative.outputStep.canBeMultiple()
    override fun requiresSingularQueryInput(): Boolean = true

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return MultiplexedOutputSerializer(
            this,
            listOf(
                getProducer().getOutputSerializer(serializationContext).upcast(),
                alternative.getElementOutputSerializer(serializationContext).upcast(),
            ),
        )
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(alternative))

    @Serializable
    @SerialName("ifEmpty")
    data class Descriptor(val alternative: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IfEmptyStep<Any?, Any?>(context.getOrCreateQuery(alternative) as UnboundQuery<Unit, *, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(alternative))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(alternative)
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.ifEmpty { $alternative }"
    }
}

@JvmName("ifEmpty_mono_mono")
fun <In : Out, Out> IMonoStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IMonoStep<Out> {
    return IfEmptyStep<In, Out>(buildMonoQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_flux_mono")
fun <In : Out, Out> IFluxStep<In>.ifEmpty(alternative: () -> IMonoStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildMonoQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_mono_flux")
fun <In : Out, Out> IMonoStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildFluxQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}

@JvmName("ifEmpty_flux_flux")
fun <In : Out, Out> IFluxStep<In>.ifEmptyFlux(alternative: () -> IFluxStep<Out>): IFluxStep<Out> {
    return IfEmptyStep<In, Out>(buildFluxQuery<Unit, Out> { alternative() }.castToInstance()).also { connect(it) }
}
