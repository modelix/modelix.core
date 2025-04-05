package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface IStreamBuilder {
    fun zero(): IStream.Completable
    fun <T> empty(): IStream.ZeroOrOne<T>
    fun <T> of(element: T): IStream.One<T>
    fun <T> of(vararg elements: T): IStream.Many<T> = many(elements)
    fun <T : Any> ofNotNull(element: T?): IStream.ZeroOrOne<T> = if (element == null) empty() else of(element)

    fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T>

    fun <T> many(elements: Collection<T>): IStream.Many<T> = many(elements.asSequence())
    fun <T> many(elements: Iterable<T>): IStream.Many<T> = many(elements.asSequence())
    fun <T> many(elements: Array<T>): IStream.Many<T> = many(elements.asSequence())
    fun many(elements: LongArray): IStream.Many<Long> = many(elements.asSequence())
    fun <T> many(elements: Sequence<T>): IStream.Many<T>

    fun <T> fromFlow(flow: Flow<T>): IStream.Many<T>

    fun <T, R> zip(input: List<IStream.Many<T>>, mapper: (List<T>) -> R): IStream.Many<R>
    fun <T, R> zip(input: List<IStream.One<T>>, mapper: (List<T>) -> R): IStream.One<R>
    fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2)
    }

    fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T>
}
