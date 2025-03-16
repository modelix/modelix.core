package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

class FoldingStep<In : CommonIn, Out : CommonIn, CommonIn>(private val operation: MonoUnboundQuery<IZip2Output<CommonIn, Out, In>, Out>) : AggregationStep<CommonIn, Out>() {

    private var initialValueProducer: IMonoStep<Out>? = null

    fun getInputProducer(): IProducingStep<In> = getProducer() as IProducingStep<In>
    fun getInitialValueProducer(): IMonoStep<Out> = initialValueProducer!!

    override fun validate() {
        super.validate()
        require(!getInitialValueProducer().canBeMultiple()) { "Not a mono: ${getInitialValueProducer()}" }
    }

    override fun getProducers(): List<IProducingStep<CommonIn>> {
        return super.getProducers() + listOfNotNull<IProducingStep<Out>>(initialValueProducer)
    }

    override fun addProducer(producer: IProducingStep<CommonIn>) {
        if (getProducers().isEmpty()) {
            super.addProducer(producer)
        } else {
            initialValueProducer = producer as IMonoStep<Out>
        }
    }

    override fun aggregate(input: StepStream<CommonIn>, context: IStreamInstantiationContext): IStream.One<IStepOutput<Out>> {
        return context.getOrCreateStream<Out>(getInitialValueProducer()).exactlyOne()
            .flatMapOne { initialValue: IStepOutput<Out> ->
                input.fold(initialValue) { acc, value -> fold(acc, value) }
            }
    }

    private fun fold(acc: IStepOutput<Out>, value: IStepOutput<CommonIn>): IStepOutput<Out> {
        return IStreamExecutor.getInstance().query {
            operation.asStream(QueryEvaluationContext.EMPTY, ZipStepOutput(listOf(acc, value))).exactlyOne()
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return operation.getElementOutputSerializer(serializationContext)
    }

    override fun toString(): String {
        return "${getProducer()}\n.fold(${getInitialValueProducer()}) {\n${operation.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(operation))

    @Serializable
    @SerialName("fold")
    class Descriptor(val operation: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FoldingStep(context.getOrCreateQuery(operation) as MonoUnboundQuery<IZip2Output<*, Any?, Any?>, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(operation))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(operation)
        }
    }
}

@Deprecated("Execution is inefficient. Should be replaced by reduce")
fun <In> IFluxStep<In>.fold(initial: Int, operation: IStepSharingContext.(acc: IMonoStep<Int>, it: IMonoStep<In>) -> IMonoStep<Int>) = fold(initial.asMono(), operation)

@Deprecated("Execution is inefficient. Should be replaced by reduce")
fun <In, Out> IFluxStep<In>.fold(initial: IMonoStep<Out>, operation: IStepSharingContext.(acc: IMonoStep<Out>, it: IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return FoldingStep<In, Out, Any?>(
        buildMonoQuery<IZip2Output<Any?, Out, In>, Out> { operation(it.first, it.second) }.castToInstance(),
    ).also {
        connect(it)
        initial.connect(it)
    }
}
