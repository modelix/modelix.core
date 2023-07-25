package org.modelix.modelql.core

interface IMappingContext {
    fun <T> IMonoStep<T>.shared(): IMonoStep<T>
}

interface IQueryBuilderContext<in In, out Out> : IMappingContext {
    fun IProducingStep<In>.mapRecursive(): IFluxStep<Out>
}

class QueryBuilderContext<In, Out, Q : IUnboundQuery<*, *, *>> : IQueryBuilderContext<In, Out> {
    val queryReference = QueryReference<IUnboundQuery<In, *, Out>>(null, null, null)
    val inputStep = computeWith { QueryInput<In>() }

    val sharedSteps = ArrayList<SharedStep<*>>()

    override fun IProducingStep<In>.mapRecursive(): IFluxStep<Out> = QueryCallStep<In, Out>(queryReference).also { connect(it) }
    fun <T> computeWith(body: QueryBuilderContext<In, Out, Q>.() -> T): T {
        return CONTEXT_VALUE.computeWith(this) {
            body()
        }
    }

    override fun <T> IMonoStep<T>.shared(): IMonoStep<T> {
        val shared: SharedStep<T> = SharedStep<T>().also { connect(it) }
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
    }
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
    }
}
