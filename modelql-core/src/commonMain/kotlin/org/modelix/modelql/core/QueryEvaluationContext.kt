package org.modelix.modelql.core

import com.badoo.reaktive.single.Single

data class QueryEvaluationContext private constructor(private val values: Map<IProducingStep<*>, Any?>) {

    fun <T> getValue(producer: IProducingStep<T>): Single<List<IStepOutput<T>>> {
        return values.getValue(producer) as Single<List<IStepOutput<T>>>
    }

    fun hasValue(producer: IProducingStep<*>): Boolean = values.containsKey(producer)

    operator fun <T> plus(entry: Pair<IProducingStep<T>, Single<List<IStepOutput<T>>>>): QueryEvaluationContext {
        return QueryEvaluationContext(values + entry)
    }

    operator fun plus(other: QueryEvaluationContext): QueryEvaluationContext {
        if (this == other) return this
        if (other.values.isEmpty()) return this
        if (this.values.isEmpty()) return other
        val combinedValues = values + other.values
        if (combinedValues == this.values) return this
        if (combinedValues == other.values) return other
        return QueryEvaluationContext(combinedValues)
    }

    fun <T> plus(producer: IProducingStep<T>, value: Single<List<IStepOutput<T>>>): QueryEvaluationContext {
        return plus(producer to value)
    }

    companion object {
        val EMPTY = QueryEvaluationContext(emptyMap())
        fun <T> of(entry: Pair<IProducingStep<T>, IStepOutput<T>>) = QueryEvaluationContext(mapOf(entry))
        fun <T> of(producer: IProducingStep<T>, value: IStepOutput<T>) = of(producer to value)
    }
}
