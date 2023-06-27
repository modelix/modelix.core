package org.modelix.modelql.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

interface IStep {
    @Deprecated("")
    var owningQuery: IUnboundQuery<*, *, *>?
    fun validate() {}

    fun createDescriptor(): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")

    fun requiresWriteAccess(): Boolean = false
    fun hasSideEffect(): Boolean = requiresWriteAccess()
    fun needsCoroutineScope() = false
}

interface IFlowInstantiationContext {
    val coroutineScope: CoroutineScope
    fun <T> getOrCreateFlow(step: IProducingStep<T>): Flow<T>
    fun <T> getFlow(step: IProducingStep<T>): Flow<T>?
}
class FlowInstantiationContext(private val coroutineScope_: CoroutineScope?) : IFlowInstantiationContext {
    override val coroutineScope: CoroutineScope get() = coroutineScope_!!
    private val createdProducers = HashMap<IProducingStep<*>, Flow<*>>()
    fun <T> put(step: IProducingStep<T>, producer: Flow<T>) {
        createdProducers[step] = producer
    }
    override fun <T> getOrCreateFlow(step: IProducingStep<T>): Flow<T> {
        return (createdProducers as MutableMap<IProducingStep<T>, Flow<T>>)
            .getOrPut(step) { step.createFlow(this) }
    }

    override fun <T> getFlow(step: IProducingStep<T>): Flow<T>? {
        return (createdProducers as MutableMap<IProducingStep<T>, Flow<T>>)[step]
    }
}

interface IProducingStep<out E> : IStep {
    fun addConsumer(consumer: IConsumingStep<E>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E>
    fun createFlow(context: IFlowInstantiationContext): Flow<E>
    fun outputIsConsumedMultipleTimes(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.inputIsConsumedMultipleTimes() }
    }
    fun canBeEmpty(): Boolean = true
    fun canBeMultiple(): Boolean = true
}
fun <T> IProducingStep<T>.connect(consumer: IConsumingStep<T>) = addConsumer(consumer)
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.addConsumer(this)

interface IMonoStep<out E> : IProducingStep<E> {
    fun evaluate(input: Any?): E = throw UnsupportedOperationException()
}

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

    override fun addProducer(producer: IProducingStep<In>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<In>> {
        return listOfNotNull(producer)
    }

    fun getProducer(): IProducingStep<In> = producer!!

    protected abstract fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out>

    override fun createFlow(context: IFlowInstantiationContext): Flow<Out> {
        return createFlow(context.getOrCreateFlow(getProducer()), context)
    }
}

abstract class MonoTransformingStep<In, Out> : TransformingStep<In, Out>(), IMonoStep<Out>, IFluxStep<Out> {
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()
    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()
    fun connectAndDowncast(producer: IMonoStep<In>): IMonoStep<Out> = also { producer.connect(it) }
    fun connectAndDowncast(producer: IFluxStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class FluxTransformingStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    fun connectAndDowncast(producer: IProducingStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class AggregationStep<In, Out> : MonoTransformingStep<In, Out>() {
    fun connectAndDowncast(producer: IProducingStep<In>): IMonoStep<Out> = also { producer.connect(it) }

    override fun canBeEmpty(): Boolean = false

    override fun canBeMultiple(): Boolean = false

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        val flow = flow {
            emit(aggregate(input))
        }
        return if (outputIsConsumedMultipleTimes()) {
            flow.shareIn(context.coroutineScope, SharingStarted.Lazily, 1)
                .take(1) // The shared flow seems to ignore that there are no more elements and keeps the subscribers active.
        } else {
            flow
        }
    }

    override fun needsCoroutineScope() = outputIsConsumedMultipleTimes()

    override fun inputIsConsumedMultipleTimes(): Boolean {
        return false
    }

    protected abstract suspend fun aggregate(input: Flow<In>): Out
}
