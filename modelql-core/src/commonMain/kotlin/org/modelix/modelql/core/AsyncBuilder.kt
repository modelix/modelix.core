package org.modelix.modelql.core

typealias ResultHandler<ContextT> = ContextT.() -> Unit

interface IAsyncBuilder<In, ContextT> {
    val input: IMonoStep<In>
    fun onSuccess(body: ResultHandler<ContextT>)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>
    fun <T> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, ContextT>.() -> Unit): IIterationRequest
    fun <T> IFluxStep<T>.iterateLater(body: IAsyncBuilder<T, ContextT>.() -> Unit): IIterationRequest
    fun ContextT.iterate(request: IIterationRequest)
}

interface IValueRequest<E> {
    fun get(): E
}

interface IIterationRequest

class AsyncBuilder<E, ContextT>(override val input: IMonoStep<E>) : IAsyncBuilder<E, ContextT> {
    private val valueRequests = ArrayList<ValueRequest<Any?>>()
    private val iterationRequests = ArrayList<IterationRequest<Any?>>()
    private var resultHandler: ResultHandler<ContextT>? = null

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        val allRequestSteps: List<IMonoStep<Any?>> = valueRequests.map { it.step } + iterationRequests.map { it.outputStep }
        return zipList(*allRequestSteps.toTypedArray())
    }

    /**
     * Can be called multiple times for a list of results.
     */
    fun ContextT.processResult(result: IZipOutput<*>) {
        val allRequests: List<Request<Any?>> = valueRequests + (iterationRequests as List<Request<Any?>>)
        allRequests.zip(result.values).forEach { (request, value) ->
            request.set(value)
        }

        resultHandler?.invoke(this)

        result.values
    }

    override fun onSuccess(body: ResultHandler<ContextT>) {
        resultHandler = body
    }
    override fun <T> IMonoStep<T>.getLater(): ValueRequest<T> {
        return ValueRequest(this).also { valueRequests.add(it as ValueRequest<Any?>) }
    }

    override fun <T> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, ContextT>.() -> Unit): IIterationRequest {
        return this.asFlux().iterateLater(body)
    }
    override fun <T> IFluxStep<T>.iterateLater(body: IAsyncBuilder<T, ContextT>.() -> Unit): IIterationRequest {
        lateinit var childBuilder: AsyncBuilder<T, ContextT>
        val outputStep: IMonoStep<List<IZipOutput<*>>> = this.map {
            childBuilder = AsyncBuilder<T, ContextT>(it).apply(body)
            childBuilder.compileOutputStep()
        }.toList()
        return IterationRequest(childBuilder, outputStep).also { iterationRequests.add(it as IterationRequest<Any?>) }
    }

    override fun ContextT.iterate(request: IIterationRequest) {
        val casted = request as IterationRequest<*>
        require(casted.getOwner() != this) { "Iteration request belongs to a different builder" }
        casted.iterate(this)
    }

    abstract class Request<E> {
        var initialized: Boolean = false
        var result: E? = null
        fun set(value: E) {
            result = value
            initialized = true
        }
        fun get(): E {
            require(initialized) { "Value not received for $this" }
            return result as E
        }
    }

    class ValueRequest<E>(val step: IMonoStep<E>) : Request<E>(), IValueRequest<E>

    inner class IterationRequest<In>(val htmlBuilder: AsyncBuilder<In, ContextT>, val outputStep: IMonoStep<List<IZipOutput<*>>>) : Request<List<IZipOutput<*>>>(), IIterationRequest {
        fun getOwner(): AsyncBuilder<*, *> = this@AsyncBuilder
        fun iterate(context: ContextT) {
            context.apply {
                get().forEach { elementResult ->
                    htmlBuilder.apply { processResult(elementResult) }
                }
            }
        }
    }
}
