package org.modelix.modelql.core

typealias FragmentBody<ContextT> = ContextT.() -> Unit

interface IFragmentBuilder<out In, out Context> {
    val input: IMonoStep<In>
    fun onSuccess(body: FragmentBody<Context>)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>

    fun <T, TContext> IMonoStep<T>.buildFragment(body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IBoundFragment<TContext>
    fun <T, TContext> IFluxStep<T>.buildFragment(body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IBoundFragment<TContext>

    @Deprecated("renamed to insertFragment", ReplaceWith("insertFragment(fragment)"))
    fun <TContext> TContext.applyFragment(fragment: IBoundFragment<TContext>)
    fun <TContext> TContext.insertFragment(fragment: IBoundFragment<TContext>) = this.applyFragment(fragment)

    fun <TIn, TContext> IMonoStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IBoundFragment<TContext>
    fun <TIn, TContext> IFluxStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IBoundFragment<TContext>
}

interface IRecursiveFragmentBuilder<In, Context> : IFragmentBuilder<In, Context>, IUnboundFragment<In, Context>

interface IBoundFragment<in Context>

fun <T, Context> IFragmentBuilder<T, Context>.castToInstance(): FragmentBuilder<T, Context> = this as FragmentBuilder<T, Context>
internal fun <T, Context> IUnboundFragment<T, Context>.castToInternal(): IUnboundFragmentInternal<T, Context> = this as IUnboundFragmentInternal<T, Context>

class FragmentBuilder<E, Context> : IRecursiveFragmentBuilder<E, Context>, IUnboundFragmentInternal<E, Context> {
    override val input: QueryInput<E> = QueryInput()
    private val zipBuilder = ZipBuilder()
    private var resultHandlers = ArrayList<FragmentBody<Context>>()
    override val queryReference = QueryReference<IMonoUnboundQuery<E, IZipOutput<*>>>(null, null, { getQuery() })
    private var query: IMonoUnboundQuery<E, IZipOutput<*>>? = null
    private var sealed = false

    fun getQuery(): IMonoUnboundQuery<E, IZipOutput<*>> {
        checkSealed()
        return query!!
    }

    fun checkNotSealed() {
        if (sealed) throw IllegalStateException("already sealed")
    }

    fun checkSealed() {
        if (!sealed) throw IllegalStateException("not sealed yet")
    }

    fun seal() {
        sealed = true
        query = MonoUnboundQuery(input, zipBuilder.compileOutputStep(), id = null)
    }

    fun compileMappingStep(it: IMonoStep<E>): IMonoStep<IZipOutput<*>> = it.map(getQuery())

    /**
     * Can be called multiple times for a list of results.
     */
    override fun processResult(result: IZipOutput<*>, context: Context) {
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

    override fun <T, TContext> IMonoStep<T>.buildFragment(body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IBoundFragment<TContext> {
        return asFlux().buildFragment(body)
    }

    override fun <T, TContext> IFluxStep<T>.buildFragment(body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IBoundFragment<TContext> {
        val childBuilder: FragmentBuilder<T, TContext> = FragmentBuilder<T, TContext>().apply(body)
        childBuilder.seal()
        val outputStep: IMonoStep<List<IZipOutput<*>>> = this.map(childBuilder.getQuery()).toList()
        val request = zipBuilder.request(outputStep)
        return BoundFragment<T, TContext>(childBuilder, request)
    }

    override fun <TIn, TContext> IMonoStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IBoundFragment<TContext> {
        return asFlux().bindFragment(fragment)
    }

    override fun <TIn, TContext> IFluxStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IBoundFragment<TContext> {
        val inputStep: IFluxStep<TIn> = this
        val recursiveStep = QueryCallStep<TIn, IZipOutput<*>>(fragment.castToInternal().queryReference).also { inputStep.connect(it) }
        val outputStep = recursiveStep.toList()
        val request = zipBuilder.request(outputStep)
        return BoundFragment<TIn, TContext>(fragment.castToInternal(), request)
    }

    override fun bind(input: IMonoStep<E>): IBoundFragment<Context> {
        return input.bindFragment(this)
    }

    override fun <TContext> TContext.applyFragment(fragment: IBoundFragment<TContext>) {
        val casted = fragment as BoundFragment<*, TContext>
        require(casted.getOwner() != this) { "Iteration request belongs to a different builder" }
        casted.iterate(this)
    }

    private inner class BoundFragment<In, RequestContext>(val unboundFragment: IUnboundFragmentInternal<In, RequestContext>, val request: IValueRequest<List<IZipOutput<*>>>) : IBoundFragment<RequestContext> {
        fun getOwner(): FragmentBuilder<*, *> = this@FragmentBuilder
        fun iterate(context: RequestContext) {
            request.get().forEach { elementResult ->
                unboundFragment.processResult(elementResult, context)
            }
        }
    }
}

fun <In, Context> buildModelQLFragment(body: IRecursiveFragmentBuilder<In, Context>.() -> Unit): IUnboundFragment<In, Context> {
    val builder = FragmentBuilder<In, Context>()
    with(builder) {
        body()
    }
    builder.seal()
    return builder
}

interface IUnboundFragment<In, Context> {
    fun bind(input: IMonoStep<In>): IBoundFragment<Context>
}

internal interface IUnboundFragmentInternal<In, Context> {
    val queryReference: QueryReference<IMonoUnboundQuery<In, IZipOutput<*>>>
    fun processResult(result: IZipOutput<*>, context: Context)
}
