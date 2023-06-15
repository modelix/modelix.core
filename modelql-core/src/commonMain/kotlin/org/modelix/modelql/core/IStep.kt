package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

interface IStep {
    var owningQuery: IQuery<*, *>?
    fun validate() {}
    fun reset(): Unit = Unit
    fun createDescriptor(): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")
}

interface IProducingStep<out RemoteOut> : IStep {
    fun addConsumer(consumer: IConsumingStep<RemoteOut>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*>
}
fun <RemoteT> IProducingStep<RemoteT>.connect(consumer: IConsumingStep<RemoteT>) = addConsumer(consumer)
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.addConsumer(this)

interface IMonoStep<out RemoteOut> : IProducingStep<RemoteOut>
interface IFluxStep<out RemoteOut> : IProducingStep<RemoteOut>

interface IConsumingStep<in E> : IStep {
    fun addProducer(producer: IProducingStep<E>)
    fun getProducers(): List<IProducingStep<*>>
    fun onNext(element: E, producer: IProducingStep<E>)
    fun onComplete(producer: IProducingStep<E>)
}

interface ITerminalStep<out RemoteResult> : IMonoStep<RemoteResult> {
    fun getResult(): RemoteResult
}

interface IProducingAndTerminalStep<RemoteOut, out LocalOut> : ITerminalStep<RemoteOut>

interface ISourceStep<out RemoteOut> : IProducingStep<RemoteOut> {
    fun run()
}

abstract class ProducingStep<RemoteOut> : IProducingStep<RemoteOut> {
    override var owningQuery: IQuery<*, *>? = null
    private val consumers = ArrayList<IConsumingStep<RemoteOut>>()

    override fun addConsumer(consumer: IConsumingStep<RemoteOut>) {
        if (consumers.contains(consumer)) return
        consumers += consumer
        consumer.addProducer(this)
    }

    override fun getConsumers(): List<IConsumingStep<*>> {
        return consumers
    }

    protected fun forwardToConsumers(element: RemoteOut) {
        consumers.forEach { it.onNext(element, this) }
    }
    protected fun completeConsumers() {
        consumers.forEach { it.onComplete(this) }
    }
}

abstract class TransformingStep<RemoteIn, RemoteOut> : IConsumingStep<RemoteIn>, ProducingStep<RemoteOut>() {
    private val producers = ArrayList<IProducingStep<RemoteIn>>()

    override fun addProducer(producer: IProducingStep<RemoteIn>) {
        if (producers.contains(producer)) return
        producers += producer
        producer.addConsumer(this)
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return producers
    }

    override fun onNext(element: RemoteIn, producer: IProducingStep<RemoteIn>) {
        for (outputElement in transform(element)) {
            forwardToConsumers(outputElement)
        }
    }

    override fun onComplete(producer: IProducingStep<RemoteIn>) {
        completeConsumers()
    }

    protected abstract fun transform(element: RemoteIn): Sequence<RemoteOut>
}

abstract class MonoTransformingStep<RemoteIn, RemoteOut> : TransformingStep<RemoteIn, RemoteOut>(), IMonoStep<RemoteOut>
abstract class FluxTransformingStep<RemoteIn, RemoteOut> : TransformingStep<RemoteIn, RemoteOut>(), IFluxStep<RemoteOut>


