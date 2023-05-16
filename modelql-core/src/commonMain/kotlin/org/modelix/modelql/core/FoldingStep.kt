package org.modelix.modelql.core

abstract class FoldingStep<RemoteIn, RemoteResult>(private val initial: RemoteResult) : IConsumingStep<RemoteIn>, ProducingStep<RemoteResult>(), ITerminalStep<RemoteResult> {
    private var producer: IProducingStep<RemoteIn>? = null

    override fun addProducer(producer: IProducingStep<RemoteIn>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    override fun onNext(element: RemoteIn, producer: IProducingStep<RemoteIn>) {
        result  = fold(result, element)
    }

    override fun onComplete(producer: IProducingStep<RemoteIn>) {
        forwardToConsumers(result)
        completeConsumers()
    }

    override fun reset() {
        result = initial
    }

    private var result: RemoteResult = initial

    protected abstract fun fold(s: RemoteResult, a: RemoteIn): RemoteResult

    override fun getResult(): RemoteResult {
        return result
    }
}