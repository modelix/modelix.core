/*
 * Copyright (c) 2024.
 *
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

import org.modelix.kotlin.utils.ContextValue
import org.modelix.streams.exactlyOne
import org.modelix.streams.getSynchronous

interface IMemoizationPersistence {
    fun <In, Out> getMemoizer(query: MonoUnboundQuery<In, Out>): Memoizer<In, Out>

    companion object {
        val CONTEXT_INSTANCE = ContextValue<IMemoizationPersistence>(NoMemoizationPersistence())
    }

    interface Memoizer<In, Out> {
        fun memoize(input: IStepOutput<In>): IStepOutput<Out>
    }
}

class NoMemoizationPersistence : IMemoizationPersistence {
    override fun <In, Out> getMemoizer(query: MonoUnboundQuery<In, Out>): IMemoizationPersistence.Memoizer<In, Out> {
        return object : IMemoizationPersistence.Memoizer<In, Out> {
            override fun memoize(input: IStepOutput<In>): IStepOutput<Out> {
                return query.asFlow(QueryEvaluationContext.EMPTY, input).exactlyOne().getSynchronous()
            }
        }
    }
}
