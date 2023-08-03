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
