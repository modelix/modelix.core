package org.modelix.modelql.core

typealias FragmentBody<ContextT> = ContextT.() -> Unit

interface IAsyncBuilder<out In, out Context> {
    val input: IMonoStep<In>
    fun onSuccess(body: FragmentBody<Context>)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>

//    fun <TIn, TContext> IFluxStep<TIn>.prepareRecursive(): IPreparedFragment<Context> = prepareRecursive(this@IAsyncBuilder)
    fun <TIn, TContext> IFluxStep<TIn>.prepareRecursive(builderToCall: IAsyncBuilder<TIn, TContext>): IPreparedFragment<TContext>
    fun <TIn, TContext> IMonoStep<TIn>.prepareRecursive(builderToCall: IAsyncBuilder<TIn, TContext>): IPreparedFragment<TContext> = asFlux().prepareRecursive(builderToCall)

    fun <T, TContext> IMonoStep<T>.prepareFragment(body: IAsyncBuilder<T, TContext>.() -> Unit): IPreparedFragment<TContext>
    fun <T, TContext> IFluxStep<T>.prepareFragment(body: IAsyncBuilder<T, TContext>.() -> Unit): IPreparedFragment<TContext>
    fun <TContext> TContext.applyFragment(request: IPreparedFragment<TContext>)

    fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IMonoStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate>
    fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IFluxStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate>
    fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext, *>)
}

interface IPreparedFragment<in Context>

fun <T, Context> IAsyncBuilder<T, Context>.castToInstance(): AsyncBuilder<T, Context> = this as AsyncBuilder<T, Context>

class AsyncBuilder<E, Context> : IAsyncBuilder<E, Context> {
    override val input: QueryInput<E> = QueryInput()
    private val zipBuilder = ZipBuilder()
    private var resultHandlers = ArrayList<FragmentBody<Context>>()
    private val queryReference = QueryReference<IMonoUnboundQuery<E, IZipOutput<*>>>(null, null)
    private val query: IMonoUnboundQuery<E, IZipOutput<*>> by lazy {
        MonoUnboundQuery(input, zipBuilder.compileOutputStep(), id = null).also {
            queryReference.query = it
            queryReference.queryId = it.id
        }
    }

    fun compileMappingStep(it: IMonoStep<E>): IMonoStep<IZipOutput<*>> = it.map(query)

    /**
     * Can be called multiple times for a list of results.
     */
    fun processResult(result: IZipOutput<*>, context: Context) {
        zipBuilder.withResult(result) {
            resultHandlers.forEach { it.invoke(context) }
        }
    }

    override fun onSuccess(body: FragmentBody<Context>) {
        resultHandlers += body
    }
    override fun <T> IMonoStep<T>.getLater(): IValueRequest<T> {
        val actual = this.getRootInputStep()
        val expected = input.getRootInputStep()
        require(expected == actual) {
            """step uses input from a different builder: $this
                |  expected input: $expected
                |  actual input: $actual
            """.trimMargin()
        }
        return zipBuilder.request(this)
    }

    override fun <T, TContext> IMonoStep<T>.prepareFragment(body: IAsyncBuilder<T, TContext>.() -> Unit): IPreparedFragment<TContext> {
        return this.asFlux().prepareFragment(body)
    }

    override fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IMonoStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate> {
        return asFlux().prepare(template)
    }

    override fun <TIn, TContext, TTemplate : IModelQLTemplate<TIn, TContext>> IFluxStep<TIn>.prepare(template: TTemplate): IModelQLTemplateInstance<TContext, TTemplate> {
        val fragment: IPreparedFragment<TContext> = prepareFragment {
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
                    applyFragment(fragment)
                }
            }
        }
    }

    override fun <T, TContext> IFluxStep<T>.prepareFragment(body: IAsyncBuilder<T, TContext>.() -> Unit): IPreparedFragment<TContext> {
        val childBuilder: AsyncBuilder<T, TContext> = AsyncBuilder<T, TContext>().apply(body)
        val outputStep: IMonoStep<List<IZipOutput<*>>> = this.map(childBuilder.query).toList()
        val request = zipBuilder.request(outputStep)
        return PreparedFragment<T, TContext>(childBuilder, request)
    }

    override fun <TIn, TContext> IFluxStep<TIn>.prepareRecursive(builderToCall: IAsyncBuilder<TIn, TContext>): IPreparedFragment<TContext> {
        val inputStep: IFluxStep<TIn> = this
        val recursiveStep = RecursiveQueryStep<TIn, IZipOutput<*>>(builderToCall.castToInstance().queryReference).also { inputStep.connect(it) }
        val outputStep = recursiveStep.toList()
        val request = zipBuilder.request(outputStep)
        return PreparedFragment<TIn, TContext>(builderToCall.castToInstance(), request)
    }

    override fun <TContext> TContext.applyFragment(request: IPreparedFragment<TContext>) {
        val casted = request as PreparedFragment<*, TContext>
        require(casted.getOwner() != this) { "Iteration request belongs to a different builder" }
        casted.iterate(this)
    }

    override fun <TContext> TContext.applyTemplate(templateInstance: IModelQLTemplateInstance<TContext, *>) {
        templateInstance.applyInstance(this)
    }

    inner class PreparedFragment<In, RequestContext>(val htmlBuilder: AsyncBuilder<In, RequestContext>, val request: IValueRequest<List<IZipOutput<*>>>) : IPreparedFragment<RequestContext> {
        fun getOwner(): AsyncBuilder<*, *> = this@AsyncBuilder
        fun iterate(context: RequestContext) {
            request.get().forEach { elementResult ->
                htmlBuilder.processResult(elementResult, context)
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
