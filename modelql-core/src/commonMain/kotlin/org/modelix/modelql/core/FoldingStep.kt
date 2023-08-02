package org.modelix.modelql.core

import kotlinx.coroutines.flow.fold

abstract class FoldingStep<In, Out>(private val initial: Out) : AggregationStep<In, Out>() {

    override suspend fun aggregate(input: StepFlow<In>): IStepOutput<Out> {
        return input.fold(initial) { acc, value -> fold(acc, value.value) }.asStepOutput(this)
    }

    override fun aggregate(input: Sequence<IStepOutput<In>>): IStepOutput<Out> {
        return input.fold(initial) { acc, value -> fold(acc, value.value) }.asStepOutput(this)
    }

    private var result: Out = initial

    protected abstract fun fold(acc: Out, value: In): Out
}
