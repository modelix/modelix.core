package org.modelix.streams

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.modelix.kotlin.utils.DelicateModelixApi

class EmptyCompletableStream() : IStream.Completable {

    override fun convert(converter: IStreamBuilder): IStream.Completable {
        return converter.zero()
    }

    @DelicateModelixApi
    override fun executeSynchronous() {
    }

    override fun andThen(other: IStream.Completable): IStream.Completable {
        return other
    }

    override fun <R> plus(other: IStream.Many<R>): IStream.Many<R> {
        return other
    }

    override fun <R> plus(other: IStream.ZeroOrOne<R>): IStream.ZeroOrOne<R> {
        return other
    }

    override fun <R> plus(other: IStream.One<R>): IStream.One<R> {
        return other
    }

    override fun <R> plus(other: IStream.OneOrMany<R>): IStream.OneOrMany<R> {
        return other
    }

    override fun asFlow(): Flow<Any?> {
        return emptyFlow()
    }

    override fun asSequence(): Sequence<Any?> {
        return emptySequence()
    }

    override fun toList(): IStream.One<List<Any?>> {
        return SingleValueStream(emptyList())
    }

    @DelicateModelixApi
    override fun iterateSynchronous(visitor: (Any?) -> Unit) {
    }

    @DelicateModelixApi
    override suspend fun iterateSuspending(visitor: suspend (Any?) -> Unit) {
    }
}
