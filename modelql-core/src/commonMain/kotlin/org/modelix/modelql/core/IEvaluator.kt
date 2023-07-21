/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface IEvaluator<out Out> {
    val context: QueryEvaluationContext
    fun createFlow(): Flow<Out>
    fun createSequence(): Sequence<Out>
    fun single(): Out
    fun optionalMono(): Optional<Out>
    fun isMultiple(): Boolean
    fun isOptional(): Boolean

    /** If isAsync() returns true then it's only legal to call createFlow() */
    fun isAsync(): Boolean = false
}

fun IEvaluator<*>.isSingle(): Boolean = !isMultiple() && !isOptional()

abstract class AbstractEvaluator<out Out>(
    override val context: QueryEvaluationContext
) : IEvaluator<Out>

class SimpleStepOutputEvaluator<E>(val input: IEvaluator<E>) : TransformingEvaluator<E, IStepOutput<E>>(input.context, input) {
    override fun mapElement(inputElement: E): IStepOutput<E> {
        return SimpleStepOutput(inputElement)
    }
}

class StepOutputValueEvaluator<E>(val input: IEvaluator<IStepOutput<E>>) : TransformingEvaluator<IStepOutput<E>, E>(input.context, input) {
    override fun mapElement(inputElement: IStepOutput<E>): E {
        return inputElement.value
    }
}

fun <T> IEvaluator<T>.wrap(): IEvaluator<IStepOutput<T>> {
    return if (this is StepOutputValueEvaluator) this.input else SimpleStepOutputEvaluator(this)
}
fun <T> IEvaluator<IStepOutput<T>>.unwrap(): IEvaluator<T> {
    return if (this is SimpleStepOutputEvaluator) this.input else StepOutputValueEvaluator(this)
}

abstract class TransformingEvaluator<in In, out Out>(context: QueryEvaluationContext, private val input: IEvaluator<In>) : AbstractEvaluator<Out>(context) {
    protected val combinedContext = input.context + context
    protected abstract fun mapElement(inputElement: In): Out

    override fun createFlow(): Flow<Out> {
        return input.createFlow().map { mapElement(it) }
    }

    override fun createSequence(): Sequence<Out> {
        return input.createSequence().map { mapElement(it) }
    }

    override fun single(): Out {
        return mapElement(input.single())
    }

    override fun optionalMono(): Optional<Out> {
        return input.optionalMono().map { mapElement(it) }
    }

    override fun isMultiple(): Boolean {
        return input.isMultiple()
    }

    override fun isOptional(): Boolean {
        return input.isOptional()
    }

    override fun isAsync(): Boolean {
        return input.isAsync()
    }
}

class SingleValueEvaluator<out Out>(context: QueryEvaluationContext, private val value: Out) : AbstractEvaluator<Out>(context) {
    override fun createFlow(): Flow<Out> = flowOf(value)
    override fun createSequence(): Sequence<Out> = sequenceOf(value)
    override fun single(): Out = value
    override fun optionalMono(): Optional<Out> = Optional.of(value)
    override fun isMultiple(): Boolean = false
    override fun isOptional(): Boolean = false
}

class EmptyEvaluator<out Out>(context: QueryEvaluationContext) : AbstractEvaluator<Out>(context) {
    override fun createFlow(): Flow<Out> = emptyFlow()
    override fun createSequence(): Sequence<Out> = emptySequence()
    override fun single(): Out = throw IllegalStateException("empty output")
    override fun optionalMono(): Optional<Out> = Optional.empty()
    override fun isMultiple(): Boolean = false
    override fun isOptional(): Boolean = true
}

class FlowEvaluator<out Out>(context: QueryEvaluationContext, private val flow: Flow<Out>) : AbstractEvaluator<Out>(context) {
    override fun createFlow(): Flow<Out> = flow
    override fun createSequence(): Sequence<Out> = throw UnsupportedOperationException()
    override fun single(): Out = throw UnsupportedOperationException()
    override fun optionalMono(): Optional<Out> = throw UnsupportedOperationException()
    override fun isMultiple(): Boolean = true
    override fun isOptional(): Boolean = true
    override fun isAsync(): Boolean = true
}

class SequenceEvaluator<out Out>(context: QueryEvaluationContext, private val sequence: Sequence<Out>) : AbstractEvaluator<Out>(context) {
    override fun createFlow(): Flow<Out> = sequence.asFlow()
    override fun createSequence(): Sequence<Out> = sequence
    override fun single(): Out = sequence.single()
    override fun optionalMono(): Optional<Out> = sequence.map { Optional.of(it) }.ifEmpty { sequenceOf(Optional.empty()) }.single()
    override fun isMultiple(): Boolean = true
    override fun isOptional(): Boolean = true
    override fun isAsync(): Boolean = false
}
