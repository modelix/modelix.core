package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.streams.IStream
import kotlin.reflect.KType

interface IStep {
    val owner: QueryReference<*>
    fun validate() {}

    fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")

    fun requiresWriteAccess(): Boolean = false
    fun hasSideEffect(): Boolean = requiresWriteAccess()
    fun requiresSingularQueryInput(): Boolean

    fun getRootInputSteps(): Set<IStep> = if (this is IConsumingStep<*>) getProducers().flatMap { it.getRootInputSteps() }.toSet() else setOf(this)
}

interface IStreamInstantiationContext {
    val evaluationContext: QueryEvaluationContext
    fun <T> getOrCreateStream(step: IProducingStep<T>): StepStream<T>
    fun <T> getStream(step: IProducingStep<T>): IStream.Many<T>?
}
class StreamInstantiationContext(
    override var evaluationContext: QueryEvaluationContext,
    val query: UnboundQuery<*, *, *>,
) : IStreamInstantiationContext {
    private val createdProducers = HashMap<IProducingStep<*>, IStream.Many<*>>()
    fun <T> put(step: IProducingStep<T>, producer: IStream.Many<T>) {
        createdProducers[step] = producer
    }
    override fun <T> getOrCreateStream(step: IProducingStep<T>): StepStream<T> {
        if (evaluationContext.hasValue(step)) return evaluationContext.getValue(step).flatMapIterable { it }
        return (createdProducers as MutableMap<IProducingStep<T>, StepStream<T>>)
            .getOrPut(step) { step.createStream(this) }
    }

    override fun <T> getStream(step: IProducingStep<T>): IStream.Many<T>? {
        return (createdProducers as MutableMap<IProducingStep<T>, IStream.Many<T>>)[step]
    }
}

/**
 * The output serializer of a query is context dependent when used by multiple `QueryCallStep`s.
 * This class carries the correct serializer for the input of a query depending on from where it is called.
 */
class SerializationContext(val serializersModule: SerializersModule, val queryInputSerializers: Map<QueryInput<*>, KSerializer<IStepOutput<*>>> = emptyMap()) {
    operator fun <T> plus(queryInputSerializer: Pair<QueryInput<T>, KSerializer<out IStepOutput<T>>>): SerializationContext {
        return SerializationContext(
            serializersModule,
            queryInputSerializers + (queryInputSerializer.first to queryInputSerializer.second.upcast()),
        )
    }

    public inline fun <reified T> serializer(): KSerializer<T> {
        return serializersModule.serializer<T>()
    }

    public fun serializer(type: KType): KSerializer<Any?> = serializersModule.serializer(type)
}

interface IProducingStep<out E> : IStep {
    fun addConsumer(consumer: IConsumingStep<E>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>>
    fun createStream(context: IStreamInstantiationContext): StepStream<E>

    fun outputIsConsumedMultipleTimes(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.inputIsConsumedMultipleTimes() }
    }

    fun canBeEmpty(): Boolean = true
    fun canBeMultiple(): Boolean = true
    fun canEvaluateStatically(): Boolean = false
    fun evaluateStatically(): E = throw UnsupportedOperationException()
    override fun requiresSingularQueryInput(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.requiresSingularQueryInput() }
    }
}
fun IProducingStep<*>.isSingle(): Boolean = !canBeEmpty() && !canBeMultiple()

fun <T> IProducingStep<T>.connect(consumer: IConsumingStep<T>) {
    val producer: IProducingStep<T> = this
    if (consumer.owner != this.owner && producer !is SharedStep<*>) {
        val producerContext = QueryBuilderContext.CONTEXT_VALUE.getAllValues().find { it.queryReference == producer.owner }
        checkNotNull(producerContext) { "Step belongs to a different query that is already finalized: $producer" }
        producerContext.computeWith {
            with(producerContext) { producer.shared() }
        }.connect(consumer)
        return
    }
    addConsumer(consumer)
}
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.connect(this)

interface IMonoStep<out E> : IProducingStep<E>

interface IFluxStep<out E> : IProducingStep<E>
interface IFluxOrMonoStep<out E> : IMonoStep<E>, IFluxStep<E>

interface IConsumingStep<in E> : IStep {
    fun addProducer(producer: IProducingStep<E>)
    fun getProducers(): List<IProducingStep<*>>
    fun inputIsConsumedMultipleTimes(): Boolean = if (this is IProducingStep<*>) outputIsConsumedMultipleTimes() else false
}

interface IProcessingStep<In, Out> : IConsumingStep<In>, IProducingStep<Out> {
    override fun inputIsConsumedMultipleTimes(): Boolean {
        return outputIsConsumedMultipleTimes()
    }
}

abstract class ProducingStep<E> : IProducingStep<E> {
    override val owner: QueryReference<*> = QueryReference.CONTEXT_VALUE.getValue()
    private val consumers = ArrayList<IConsumingStep<E>>()

    override fun addConsumer(consumer: IConsumingStep<E>) {
        if (consumers.contains(consumer)) return
        if (consumer.owner != this.owner && this !is SharedStep<*>) {
            throw CrossQueryReferenceException("Cannot consume step from a different query (${consumer.owner.queryId} != ${this.owner.queryId}): $this")
        }
        consumers += consumer
        consumer.addProducer(this)
    }

    override fun getConsumers(): List<IConsumingStep<E>> {
        return consumers
    }
}

abstract class TransformingStep<In, Out> : IProcessingStep<In, Out>, ProducingStep<Out>() {

    private var producer: IProducingStep<In>? = null

    override fun validate() {
        super<ProducingStep>.validate()
        require(producer != null) { "Step has no input: ${this::class}" }
    }

    override fun addProducer(producer: IProducingStep<In>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<In>> {
        return listOfNotNull(producer)
    }

    fun getProducer(): IProducingStep<In> = producer!!

    protected abstract fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out>

    override fun createStream(context: IStreamInstantiationContext): StepStream<Out> {
        return createStream(context.getOrCreateStream(getProducer()), context)
    }
}

abstract class MonoTransformingStep<In, Out> : TransformingStep<In, Out>(), IMonoStep<Out>, IFluxStep<Out> {
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()
    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    fun connectAndDowncast(producer: IMonoStep<In>): IMonoStep<Out> = also { producer.connect(it) }
    fun connectAndDowncast(producer: IFluxStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class SimpleMonoTransformingStep<In, Out> : MonoTransformingStep<In, Out>() {
    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        return input.map { transform(context.evaluationContext, it.value).asStepOutput(this) }
    }
    abstract fun transform(evaluationContext: QueryEvaluationContext, input: In): Out
}

abstract class FluxTransformingStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    fun connectAndDowncast(producer: IProducingStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class AggregationStep<In, Out> : MonoTransformingStep<In, Out>() {
    fun connectAndDowncast(producer: IProducingStep<In>): IMonoStep<Out> = also { producer.connect(it) }

    override fun canBeEmpty(): Boolean = false

    override fun canBeMultiple(): Boolean = false
    override fun requiresSingularQueryInput(): Boolean = true

    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        val aggregated = aggregate(input, context)
        return (if (outputIsConsumedMultipleTimes()) aggregated.cached() else aggregated)
    }

    override fun inputIsConsumedMultipleTimes(): Boolean {
        return false
    }

    protected abstract fun aggregate(input: StepStream<In>, context: IStreamInstantiationContext): IStream.One<IStepOutput<Out>>
}
