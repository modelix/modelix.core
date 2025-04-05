package org.modelix.streams

import org.modelix.kotlin.utils.DelicateModelixApi

interface IStreamInternal<out E> : IStream<E> {
    /**
     * Should only be used inside implementations of [IStreamExecutor].
     * Use [IStreamExecutor] instead.
     *
     * Will only succeed if all the input data is available locally and there isn't any asynchronous request necessary.
     *
     */
    @DelicateModelixApi
    fun iterateBlocking(visitor: (E) -> Unit)

    /**
     * Should only be used inside implementations of [IStreamExecutor].
     * Use [IStreamExecutor] instead.
     *
     * If called directly it may bypass performance optimizations of the [IStreamExecutor] (bulk requests).
     */
    @DelicateModelixApi
    suspend fun iterateSuspending(visitor: suspend (E) -> Unit)

    interface Completable : IStreamInternal<Any?>, IStream.Completable {
        /**
         * See documentation of [iterateBlocking].
         */
        @DelicateModelixApi
        fun executeBlocking()

        @DelicateModelixApi
        suspend fun executeSuspending()
    }

    interface ZeroOrOne<out E> : IStreamInternal<E>, IStream.ZeroOrOne<E> {
        /**
         * See documentation of [iterateSuspending].
         */
        @DelicateModelixApi
        suspend fun getSuspending(): E?

        @DelicateModelixApi
        fun getBlocking(): E?
    }

    interface One<out E> : ZeroOrOne<E>, IStream.One<E> {
        /**
         * If no suspending requests are necessary it will behave like getSynchronous
         *
         * See documentation of [getSuspending]
         */
        @DelicateModelixApi
        override fun getBlocking(): E

        /**
         * See documentation of [iterateSuspending].
         */
        @DelicateModelixApi
        override suspend fun getSuspending(): E
    }
}
