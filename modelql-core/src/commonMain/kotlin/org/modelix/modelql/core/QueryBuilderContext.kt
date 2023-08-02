package org.modelix.modelql.core

interface IStepSharingContext {
    fun <T> IMonoStep<T>.shared(): IMonoStep<T>
    fun <T> IFluxStep<T>.shared(): IFluxStep<T>
}

interface IQueryBuilderContext<in In, out Out> : IStepSharingContext {
    fun IProducingStep<In>.mapRecursive(): IFluxStep<Out>
}

class QueryBuilderContext<In, Out, Q : IUnboundQuery<*, *, *>> : IQueryBuilderContext<In, Out> {
    private val childContexts = ArrayList<QueryBuilderContext<*, *, *>>()
    private val parentContext: QueryBuilderContext<*, *, *>? = CONTEXT_VALUE.tryGetValue()?.also { it.childContexts.add(this) }
    val sharedSteps = ArrayList<SharedStep<*>>()
    val queryReference = QueryReference<Q>(null, null, null)
    val inputStep = computeWith { QueryInput<In>() }

    fun validateAllIfRoot() {
        if (parentContext == null) {
            validateAll()
        }
    }

    fun validateAll() {
        queryReference.providedQuery?.castToInstance()?.validate()
        childContexts.forEach { it.validateAll() }
    }

    override fun IProducingStep<In>.mapRecursive(): IFluxStep<Out> = QueryCallStep<In, Out>(queryReference as QueryReference<IUnboundQuery<In, *, Out>>).also { connect(it) }
    fun <T> computeWith(body: QueryBuilderContext<In, Out, Q>.() -> T): T {
        return CONTEXT_VALUE.computeWith(this) {
            QueryReference.CONTEXT_VALUE.computeWith(queryReference) {
                body()
            }
        }
    }

    override fun <T> IMonoStep<T>.shared(): IMonoStep<T> {
        val downcasted: IProducingStep<T> = this
        return downcasted.shared()
    }

    override fun <T> IFluxStep<T>.shared(): IFluxStep<T> {
        val downcasted: IProducingStep<T> = this
        return downcasted.shared()
    }

    fun <T> IProducingStep<T>.shared(): SharedStep<T> {
        val producer: IProducingStep<T> = this
        check(queryReference.providedQuery == null) { "Query is already created. Cannot share step anymore: $producer" }
        val existing = sharedSteps.find { it.getProducer() == producer }
        if (existing != null) return existing as SharedStep<T>
        val shared: SharedStep<T> = SharedStep<T>().also { producer.connect(it) }
        sharedSteps += shared
        return shared
    }

    companion object {
        val CONTEXT_VALUE = ContextValue<QueryBuilderContext<*, *, *>>()
    }
}

fun <In, Out> buildMonoQuery(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out, IMonoUnboundQuery<In, Out>>()
    return context.computeWith {
        val outputStep = body(context.inputStep)
        MonoUnboundQuery<In, Out>(
            inputStep,
            outputStep,
            reference = context.queryReference as QueryReference<UnboundQuery<In, Out, Out>>,
            context.sharedSteps
        )
    }.also { context.validateAllIfRoot() }
}
fun <In, Out> buildFluxQuery(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IFluxStep<Out>): IFluxUnboundQuery<In, Out> {
    val context = QueryBuilderContext<In, Out, IFluxUnboundQuery<In, Out>>()
    return context.computeWith {
        val outputStep = body(context.inputStep)
        FluxUnboundQuery<In, Out>(
            inputStep,
            outputStep,
            reference = context.queryReference as QueryReference<IFluxUnboundQuery<In, Out>>,
            context.sharedSteps
        )
    }.also { context.validateAllIfRoot() }
}
