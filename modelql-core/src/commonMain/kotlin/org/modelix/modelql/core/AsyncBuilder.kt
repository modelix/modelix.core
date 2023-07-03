package org.modelix.modelql.core

typealias ResultHandler<ContextT> = ContextT.() -> Unit

interface IAsyncBuilder<In, Context> {
    val input: IMonoStep<In>
    fun onSuccess(body: ResultHandler<Context>)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>
    fun <T, TContext> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext>
    fun <T, TContext> IFluxStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext>
    fun <TContext> TContext.iterate(request: IIterationRequest<TContext>)

    fun <TIn, TContext> IMonoStep<TIn>.prepare(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext>
    fun <TIn, TContext> IFluxStep<TIn>.prepare(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext>
    fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext>)
}

interface IValueRequest<E> {
    fun get(): E
}

interface IIterationRequest<Context>

class AsyncBuilder<E, Context>(override val input: IMonoStep<E>) : IAsyncBuilder<E, Context> {
    private val valueRequests = ArrayList<ValueRequest<Any?>>()
    private val iterationRequests = ArrayList<IterationRequest<Any?, Any?>>()
    private var resultHandler: ResultHandler<Context>? = null

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        val allRequestSteps: List<IMonoStep<Any?>> = valueRequests.map { it.step } + iterationRequests.map { it.outputStep }
        return zipList(*allRequestSteps.toTypedArray())
    }

    /**
     * Can be called multiple times for a list of results.
     */
    fun Context.processResult(result: IZipOutput<*>) {
        val allRequests: List<Request<Any?>> = valueRequests + (iterationRequests as List<Request<Any?>>)
        allRequests.zip(result.values).forEach { (request, value) ->
            request.set(value)
        }

        resultHandler?.invoke(this)

        result.values
    }

    override fun onSuccess(body: ResultHandler<Context>) {
        resultHandler = body
    }
    override fun <T> IMonoStep<T>.getLater(): ValueRequest<T> {
        return ValueRequest(this).also { valueRequests.add(it as ValueRequest<Any?>) }
    }

    override fun <T, TContext> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext> {
        return this.asFlux().iterateLater(body)
    }

    override fun <TIn, TContext> IMonoStep<TIn>.prepare(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext> {
        return asFlux().prepare(template)
    }

    override fun <TIn, TContext> IFluxStep<TIn>.prepare(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext> {
        @kotlin.Suppress("UnnecessaryVariable")
        val iterationRequest: IIterationRequest<TContext> = iterateLater { template.applyTemplate(this) }

        return object : IModelQLTemplateInstance<TContext> {
            override fun applyTemplate(context: TContext) {
                with(context) {
                    iterate(iterationRequest)
                }
            }
        }
    }

    override fun <T, TContext> IFluxStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext> {
        lateinit var childBuilder: AsyncBuilder<T, TContext>
        val outputStep: IMonoStep<List<IZipOutput<*>>> = this.map {
            childBuilder = AsyncBuilder<T, TContext>(it).apply(body)
            childBuilder.compileOutputStep()
        }.toList()
        return IterationRequest<T, TContext>(childBuilder, outputStep).also { iterationRequests.add(it as IterationRequest<Any?, Any?>) }
    }

    override fun <TContext> TContext.iterate(request: IIterationRequest<TContext>) {
        val casted = request as IterationRequest<*, TContext>
        require(casted.getOwner() != this) { "Iteration request belongs to a different builder" }
        casted.iterate(this)
    }

    override fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext>) {
        templateInstance.applyTemplate(this)
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

    inner class IterationRequest<In, RequestContext>(val htmlBuilder: AsyncBuilder<In, RequestContext>, val outputStep: IMonoStep<List<IZipOutput<*>>>) : Request<List<IZipOutput<*>>>(), IIterationRequest<RequestContext> {
        fun getOwner(): AsyncBuilder<*, *> = this@AsyncBuilder
        fun iterate(context: RequestContext) {
            context.apply {
                get().forEach { elementResult ->
                    htmlBuilder.apply { processResult(elementResult) }
                }
            }
        }
    }
}

fun <In, Context> buildModelQLTemplate(body: IAsyncBuilder<In, Context>.() -> Unit): IModelQLTemplate<In, Context> {
    return object : IModelQLTemplate<In, Context> {
        override fun applyTemplate(builder: IAsyncBuilder<In, Context>) {
            with(builder) {
                body()
            }
        }
    }
}

interface IModelQLTemplate<In, Context> {
    fun applyTemplate(builder: IAsyncBuilder<In, Context>)
}
interface IModelQLTemplateInstance<Context> {
    fun applyTemplate(context: Context)
}
