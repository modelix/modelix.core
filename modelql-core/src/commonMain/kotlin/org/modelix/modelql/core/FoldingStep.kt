package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.fold

abstract class FoldingStep<In, Out>(private val initial: Out) : AggregationStep<In, Out>() {

    override suspend fun aggregate(input: Flow<In>): Out {
        return input.fold(initial) { acc, value -> fold(acc, value) }
    }

    override fun aggregate(input: Sequence<In>): Out {
        return input.fold(initial) { acc, value -> fold(acc, value) }
    }

    private var result: Out = initial

    protected abstract fun fold(acc: Out, value: In): Out
}
