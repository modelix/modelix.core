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
                return query.asStream(QueryEvaluationContext.EMPTY, input).exactlyOne().getSynchronous()
            }
        }
    }
}
