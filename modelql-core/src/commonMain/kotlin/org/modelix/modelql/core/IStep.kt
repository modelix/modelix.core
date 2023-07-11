package org.modelix.modelql.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

interface IStep {
    var owningQuery: IUnboundQuery<*, *, *>?
    fun validate() {}

    fun createDescriptor(): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")

    fun requiresWriteAccess(): Boolean = false
    fun hasSideEffect(): Boolean = requiresWriteAccess()
    fun needsCoroutineScope() = false
    fun requiresSingularQueryInput(): Boolean

    fun getRootInputStep(): IStep = if (this is IConsumingStep<*>) getProducers().map { it.getRootInputStep() }.toSet().single() else this
}

interface IFlowInstantiationContext {
    val coroutineScope: CoroutineScope?
    fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T>
    fun <T> getFlow(step: IProducingStep<T>): Flow<T>?
}
class FlowInstantiationContext(override val coroutineScope: CoroutineScope?) : IFlowInstantiationContext {
    private val createdProducers = HashMap<IProducingStep<*>, Flow<*>>()
    fun <T> put(step: IProducingStep<T>, producer: Flow<T>) {
        createdProducers[step] = producer
    }
    override fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T> {
        return (createdProducers as MutableMap<IProducingStep<T>, StepFlow<T>>)
            .getOrPut(step) { step.createFlow(this) }
    }

    override fun <T> getFlow(step: IProducingStep<T>): Flow<T>? {
        return (createdProducers as MutableMap<IProducingStep<T>, Flow<T>>)[step]
    }
}

interface IProducingStep<out E> : IStep {
    fun addConsumer(consumer: IConsumingStep<E>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>>
    fun createFlow(context: IFlowInstantiationContext): StepFlow<E>

    /**
     * Flows usually provide better performance, but if suspending is not possible you can iterate using a sequence.
     */
    fun createSequence(queryInput: Sequence<Any?>): Sequence<E>

    /**
     * Even higher performance for producers that output exactly one element
     */
    fun evaluate(queryInput: Any?): Optional<E> {
        return createSequence(sequenceOf(queryInput))
            .map { Optional.of(it) }
            .ifEmpty { sequenceOf(Optional.empty<E>()) }
            .single()
    }

    fun outputIsConsumedMultipleTimes(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.inputIsConsumedMultipleTimes() }
    }

    fun canBeEmpty(): Boolean = true
    fun canBeMultiple(): Boolean = true
    override fun requiresSingularQueryInput(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.requiresSingularQueryInput() }
    }
}
fun IProducingStep<*>.isSingle(): Boolean = !canBeEmpty() && !canBeMultiple()

fun <T> IProducingStep<T>.connect(consumer: IConsumingStep<T>) = addConsumer(consumer)
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.addConsumer(this)

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
    override var owningQuery: IUnboundQuery<*, *, *>? = null
    private val consumers = ArrayList<IConsumingStep<E>>()

    override fun addConsumer(consumer: IConsumingStep<E>) {
        if (consumers.contains(consumer)) return
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
        require(producer != null) { "Step has no input" }
    }

    override fun addProducer(producer: IProducingStep<In>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<In>> {
        return listOfNotNull(producer)
    }

    fun getProducer(): IProducingStep<In> = producer!!

    protected abstract fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out>

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<Out> {
        return createFlow(context.getOrCreateFlow(getProducer()), context)
    }
}

abstract class MonoTransformingStep<In, Out> : TransformingStep<In, Out>(), IMonoStep<Out>, IFluxStep<Out> {
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()
    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    fun connectAndDowncast(producer: IMonoStep<In>): IMonoStep<Out> = also { producer.connect(it) }
    fun connectAndDowncast(producer: IFluxStep<In>): IFluxStep<Out> = also { producer.connect(it) }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.map { SimpleStepOutput(transform(it.value)) }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        return createTransformingSequence(getProducer().createSequence(queryInput))
    }

    open fun createTransformingSequence(input: Sequence<In>): Sequence<Out> {
        return input.map { transform(it) }
    }

    override fun evaluate(queryInput: Any?): Optional<Out> {
        return getProducer().evaluate(queryInput).map { transform(it) }
    }
    abstract fun transform(input: In): Out
}

abstract class FluxTransformingStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    fun connectAndDowncast(producer: IProducingStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class AggregationStep<In, Out> : MonoTransformingStep<In, Out>() {
    fun connectAndDowncast(producer: IProducingStep<In>): IMonoStep<Out> = also { producer.connect(it) }

    override fun canBeEmpty(): Boolean = false

    override fun canBeMultiple(): Boolean = false
    override fun requiresSingularQueryInput(): Boolean = true

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        val flow = flow {
            emit(aggregate(input))
        }
        return if (outputIsConsumedMultipleTimes()) {
            val scope = context.coroutineScope ?: throw RuntimeException("Coroutine scope require for caching of $this")
            flow.shareIn(scope, SharingStarted.Lazily, 1)
                .take(1) // The shared flow seems to ignore that there are no more elements and keeps the subscribers active.
        } else {
            flow
        }
    }

    override fun transform(input: In): Out {
        return aggregate(sequenceOf(input.asStepOutput())).value
    }

    override fun evaluate(queryInput: Any?): Optional<Out> {
        return Optional.of(aggregate(getProducer().createSequence(sequenceOf(queryInput)).map { it.asStepOutput() }).value)
    }

    override fun needsCoroutineScope() = outputIsConsumedMultipleTimes()

    override fun inputIsConsumedMultipleTimes(): Boolean {
        return false
    }

    protected abstract suspend fun aggregate(input: StepFlow<In>): IStepOutput<Out>
    protected abstract fun aggregate(input: Sequence<IStepOutput<In>>): IStepOutput<Out>
}
