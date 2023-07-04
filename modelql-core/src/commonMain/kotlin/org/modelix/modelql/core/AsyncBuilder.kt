package org.modelix.modelql.core

typealias ResultHandler<ContextT> = ContextT.() -> Unit

interface IAsyncBuilder<In, Context> {
    val input: IMonoStep<In>
    fun onSuccess(body: ResultHandler<Context>)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>
    fun <T, TContext> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext>
    fun <T, TContext> IFluxStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext>
    fun <TContext> TContext.iterate(request: IIterationRequest<TContext>)

    fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IMonoStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate>
    fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IFluxStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate>
    fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext, *>)
}

interface IValueRequest<E> {
    fun get(): E
}

interface IIterationRequest<Context>

class AsyncBuilder<E, Context>(override val input: IMonoStep<E>) : IAsyncBuilder<E, Context> {
    private val valueRequests = ArrayList<ValueRequest<Any?>>()
    private val iterationRequests = ArrayList<IterationRequest<Any?, Any?>>()
    private var resultHandlers = ArrayList<ResultHandler<Context>>()

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

        resultHandlers.forEach { it.invoke(this) }
    }

    override fun onSuccess(body: ResultHandler<Context>) {
        resultHandlers += body
    }
    override fun <T> IMonoStep<T>.getLater(): ValueRequest<T> {
        return ValueRequest(this).also { valueRequests.add(it as ValueRequest<Any?>) }
    }

    override fun <T, TContext> IMonoStep<T>.iterateLater(body: IAsyncBuilder<T, TContext>.() -> Unit): IIterationRequest<TContext> {
        return this.asFlux().iterateLater(body)
    }

    override fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IMonoStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate> {
        return asFlux().prepare(template)
    }

    override fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IFluxStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate> {
        @kotlin.Suppress("UnnecessaryVariable")
        val iterationRequest: IIterationRequest<TContext> = iterateLater {
            with(template) {
                prepareInstance()
            }
        }

        return object : IModelQLTemplateInstance<TContext, TTemplate> {
            override fun getTemplate(): TTemplate {
                return template
            }
            override fun applyInstance(context: TContext) {
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

    override fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext, *>) {
        templateInstance.applyInstance(this)
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
        override fun IAsyncBuilder<In, Context>.prepareInstance() {
            body()
        }
    }
}

interface IModelQLTemplate<In, Context> {
    fun IAsyncBuilder<In, Context>.prepareInstance()
}
interface IModelQLTemplateInstance<Context, Template : IModelQLTemplate<*, Context>> {
    fun getTemplate(): Template
    fun applyInstance(context: Context)
}
