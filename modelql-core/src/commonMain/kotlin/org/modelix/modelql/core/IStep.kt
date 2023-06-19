package org.modelix.modelql.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

interface IStep {
    @Deprecated("")
    var owningQuery: IUnboundQuery<*, *>?
    fun validate() {}

    @Deprecated("")
    fun createDescriptor(): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")

    fun requiresWriteAccess(): Boolean = false
}

interface IFlowInstantiationContext {
    val coroutineScope: CoroutineScope
    fun <T> getOrCreateFlow(step: IProducingStep<T>): Flow<T>
}
class FlowInstantiationContext(override val coroutineScope: CoroutineScope) : IFlowInstantiationContext {
    private val createdProducers = HashMap<IProducingStep<*>, Flow<*>>()
    fun <T> put(step: IProducingStep<T>, producer: Flow<T>) {
        createdProducers[step] = producer
    }
    override fun <T> getOrCreateFlow(step: IProducingStep<T>): Flow<T> {
        return (createdProducers as MutableMap<IProducingStep<T>, Flow<T>>)
            .getOrPut(step) { step.createFlow(this) }
    }
}

interface IProducingStep<out E> : IStep {
    fun addConsumer(consumer: IConsumingStep<E>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E>
    fun createFlow(context: IFlowInstantiationContext): Flow<E>
}
fun <T> IProducingStep<T>.connect(consumer: IConsumingStep<T>) = addConsumer(consumer)
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.addConsumer(this)

interface IMonoStep<out E> : IProducingStep<E>
interface IFluxStep<out E> : IProducingStep<E>

interface IConsumingStep<in E> : IStep {
    fun addProducer(producer: IProducingStep<E>)
    fun getProducers(): List<IProducingStep<*>>
}

interface IProcessingStep<In, Out> : IConsumingStep<In>, IProducingStep<Out>

abstract class ProducingStep<E> : IProducingStep<E> {
    override var owningQuery: IUnboundQuery<*, *>? = null
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

abstract class MonoTransformingStep<In, Out> : TransformingStep<In, Out>(), IMonoStep<Out>
abstract class FluxTransformingStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out>

abstract class AggregationStep<In, Out> : MonoTransformingStep<In, Out>() {
    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return flow {
            emit(aggregate(input))
        } // .shareIn(context.coroutineScope, SharingStarted.WhileSubscribed(), 1)
    }

    protected abstract suspend fun aggregate(input: Flow<In>): Out
}
