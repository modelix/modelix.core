/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.core

data class QueryEvaluationContext private constructor(private val values: Map<IProducingStep<*>, Any?>) {

    fun <T> getValue(producer: IProducingStep<T>): List<IStepOutput<T>> {
        return values.getValue(producer) as List<IStepOutput<T>>
    }

    fun hasValue(producer: IProducingStep<*>): Boolean = values.containsKey(producer)

    operator fun <T> plus(entry: Pair<IProducingStep<T>, List<IStepOutput<T>>>): QueryEvaluationContext {
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

    fun <T> plus(producer: IProducingStep<T>, value: List<IStepOutput<T>>): QueryEvaluationContext {
        return plus(producer to value)
    }

    companion object {
        val EMPTY = QueryEvaluationContext(emptyMap())
        fun <T> of(entry: Pair<IProducingStep<T>, IStepOutput<T>>) = QueryEvaluationContext(mapOf(entry))
        fun <T> of(producer: IProducingStep<T>, value: IStepOutput<T>) = of(producer to value)
    }
}
