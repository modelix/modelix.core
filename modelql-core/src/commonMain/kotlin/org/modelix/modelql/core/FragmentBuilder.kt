package org.modelix.modelql.core

typealias FragmentBody<ContextT> = ContextT.() -> Unit

interface IFragmentBuilder<out In, out Context> : IZipBuilderContext, IStepSharingContext {
    val input: IMonoStep<In>
    fun onSuccess(body: FragmentBody<Context>)

    fun <T, TContext> IMonoStep<T>.requestFragment(eager: Boolean = true, body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IRequestedFragment<TContext> {
        return requestFragment(buildModelQLFragment(eager = eager, body))
    }
    fun <T, TContext> IFluxStep<T>.requestFragment(eager: Boolean = true, body: IRecursiveFragmentBuilder<T, TContext>.() -> Unit): IRequestedFragment<TContext> {
        return requestFragment(buildModelQLFragment(eager = eager, body))
    }

    fun <TContext> TContext.insertFragment(fragment: IBoundFragment<TContext>) {
        fragment.insertInto(this)
    }
    fun <TContext> TContext.insertFragment(fragment: IRequestedFragment<TContext>) {
        fragment.get().insertInto(this)
    }

    fun <TIn, TContext> IMonoStep<TIn>.requestFragment(fragment: IUnboundFragment<TIn, TContext>): IRequestedFragment<TContext> {
        return bindFragment(fragment).request()
    }
    fun <TIn, TContext> IFluxStep<TIn>.requestFragment(fragment: IUnboundFragment<TIn, TContext>): IRequestedFragment<TContext> {
        return bindFragment(fragment).request()
    }
}

interface IRecursiveFragmentBuilder<In, Context> : IFragmentBuilder<In, Context>, IUnboundFragment<In, Context>

interface IBoundFragment<in Context> {
    fun insertInto(context: Context)
}

fun <TContext> TContext.insertFragment(fragment: IBoundFragment<TContext>) {
    fragment.insertInto(this)
}

fun <T, Context> IFragmentBuilder<T, Context>.castToInstance(): FragmentBuilder<T, Context> = this as FragmentBuilder<T, Context>
internal fun <T, Context> IUnboundFragment<T, Context>.castToInternal(): IUnboundFragmentInternal<T, Context> = this as IUnboundFragmentInternal<T, Context>

class FragmentBuilder<E, Context> : IRecursiveFragmentBuilder<E, Context>, IUnboundFragmentInternal<E, Context> {
    val queryBuilder = QueryBuilderContext<E, IZipOutput<*>, IMonoUnboundQuery<E, IZipOutput<*>>>()
    override val input: QueryInput<E> get() = queryBuilder.inputStep
    private val zipBuilder = ZipBuilder()
    private var resultHandlers = ArrayList<FragmentBody<Context>>()
    override val queryReference: QueryReference<IMonoUnboundQuery<E, IZipOutput<*>>> get() = queryBuilder.queryReference
    private var query: IMonoUnboundQuery<E, IZipOutput<*>>? = null
    private var sealed = false

    override fun <T> IMonoStep<T>.shared(): IMonoStep<T> = with(queryBuilder) { shared() }
    override fun <T> IFluxStep<T>.shared(): IFluxStep<T> = with(queryBuilder) { shared() }

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
        query = MonoUnboundQuery(
            queryBuilder.inputStep,
            zipBuilder.compileOutputStep(),
            reference = queryBuilder.queryReference,
            sharedSteps = queryBuilder.sharedSteps
        )
    }

    fun compileMappingStep(it: IMonoStep<E>): IMonoStep<IZipOutput<*>> = it.map(getQuery())

    /**
     * Can be called multiple times for a list of results.
     */
    override fun processResult(result: IZipOutput<*>, context: Context) {
        checkSealed()
        zipBuilder.withResult(result) {
            resultHandlers.forEach { it.invoke(context) }
        }
    }

    override fun onSuccess(body: FragmentBody<Context>) {
        checkNotSealed()
        resultHandlers += body
    }
    override fun <T> IMonoStep<T>.request(): IValueRequest<T> {
        checkNotSealed()
//        val actual = this.getRootInputSteps().filter { (it as? IProducingStep<*>)?.canEvaluateStatically() != true }.toSet()
//        val expected = setOf(input)
//        require(actual.isEmpty() || expected == actual) {
//            """step uses input from a different builder: $this
//                |  expected input: $expected
//                |  actual input: $actual
//            """.trimMargin()
//        }
        return zipBuilder.request(this)
    }
}

fun <In, Context> buildModelQLFragment(body: IRecursiveFragmentBuilder<In, Context>.() -> Unit): IUnboundFragment<In, Context> {
    return buildModelQLFragment(true, body)
}

fun <In, Context> buildModelQLFragment(eager: Boolean, body: IRecursiveFragmentBuilder<In, Context>.() -> Unit): IUnboundFragment<In, Context> {
    val doBuild: () -> FragmentBuilder<In, Context> = {
        val builder = FragmentBuilder<In, Context>()
        with(builder) {
            builder.queryBuilder.computeWith {
                body()
                builder.seal()
            }
        }
        builder
    }
    return if (eager) doBuild() else LazyFragment(doBuild)
}

private class BoundFragment<In, RequestContext>(val unboundFragment: IUnboundFragmentInternal<In, RequestContext>, val request: IValueRequest<List<IZipOutput<*>>>) : IBoundFragment<RequestContext> {
    override fun insertInto(context: RequestContext) {
        request.get().forEach { elementResult ->
            unboundFragment.processResult(elementResult, context)
        }
    }
}

interface IUnboundFragment<In, Context>

typealias IRequestedFragment<Context> = IValueRequest<IBoundFragment<Context>>

fun <TIn, TContext> IFluxStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IMonoStep<IBoundFragment<TContext>> {
    val inputStep: IFluxStep<TIn> = this
    val recursiveStep = QueryCallStep<TIn, IZipOutput<*>>(fragment.castToInternal().queryReference).also { inputStep.connect(it) }
    val outputStep = recursiveStep.toList()
    return outputStep.mapLocal { BoundFragment<TIn, TContext>(fragment.castToInternal(), NonRequest(it)) }
}
private class NonRequest<E>(private val value: E) : IValueRequest<E> {
    override fun get(): E = value
}
fun <TIn, TContext> IMonoStep<TIn>.bindFragment(body: IRecursiveFragmentBuilder<TIn, TContext>.() -> Unit): IMonoStep<IBoundFragment<TContext>> {
    return bindFragment(buildModelQLFragment(body))
}
fun <TIn, TContext> IFluxStep<TIn>.bindFragment(body: IRecursiveFragmentBuilder<TIn, TContext>.() -> Unit): IMonoStep<IBoundFragment<TContext>> {
    return bindFragment(buildModelQLFragment(body))
}
fun <TIn, TContext> IMonoStep<TIn>.bindFragment(fragment: IUnboundFragment<TIn, TContext>): IMonoStep<IBoundFragment<TContext>> {
    return asFlux().bindFragment(fragment)
}

internal interface IUnboundFragmentInternal<In, Context> : IUnboundFragment<In, Context> {
    val queryReference: QueryReference<IMonoUnboundQuery<In, IZipOutput<*>>>
    fun processResult(result: IZipOutput<*>, context: Context)
}

private class LazyFragment<In, Context>(fragmentBuilder: () -> FragmentBuilder<In, Context>) : IUnboundFragmentInternal<In, Context> {
    private val actualFragment: FragmentBuilder<In, Context> by lazy { fragmentBuilder() }
    override val queryReference: QueryReference<IMonoUnboundQuery<In, IZipOutput<*>>> = QueryReference(
        null,
        null,
        { actualFragment.getQuery() }
    )

    override fun processResult(result: IZipOutput<*>, context: Context) {
        return actualFragment.processResult(result, context)
    }
}
