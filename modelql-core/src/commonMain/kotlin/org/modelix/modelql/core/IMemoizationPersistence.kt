package org.modelix.modelql.core

import org.modelix.kotlin.utils.ContextValue
import org.modelix.streams.IStreamExecutor

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
                return IStreamExecutor.getInstance().query {
                    query.asStream(QueryEvaluationContext.EMPTY, input).exactlyOne()
                }
            }
        }
    }
}
