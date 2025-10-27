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

    fun <T1, T2, T3, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        mapper: (T1, T2, T3) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3)
    }

    fun <T1, T2, T3, T4, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        mapper: (T1, T2, T3, T4) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4)
    }

    fun <T1, T2, T3, T4, T5, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        source5: IStream.One<T5>,
        mapper: (T1, T2, T3, T4, T5) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4, source5)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5)
    }

    fun <T1, T2, T3, T4, T5, T6, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        source5: IStream.One<T5>,
        source6: IStream.One<T6>,
        mapper: (T1, T2, T3, T4, T5, T6) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4, source5, source6)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5, values[5] as T6)
    }

    fun <T1, T2, T3, T4, T5, T6, T7, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        source5: IStream.One<T5>,
        source6: IStream.One<T6>,
        source7: IStream.One<T7>,
        mapper: (T1, T2, T3, T4, T5, T6, T7) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4, source5, source6, source7)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5, values[5] as T6, values[6] as T7)
    }

    fun <T1, T2, T3, T4, T5, T6, T7, T8, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        source5: IStream.One<T5>,
        source6: IStream.One<T6>,
        source7: IStream.One<T7>,
        source8: IStream.One<T8>,
        mapper: (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4, source5, source6, source7, source8)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5, values[5] as T6, values[6] as T7, values[7] as T8)
    }

    fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        source3: IStream.One<T3>,
        source4: IStream.One<T4>,
        source5: IStream.One<T5>,
        source6: IStream.One<T6>,
        source7: IStream.One<T7>,
        source8: IStream.One<T8>,
        source9: IStream.One<T9>,
        mapper: (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R,
    ): IStream.One<R> = IStream.zip(listOf(source1, source2, source3, source4, source5, source6, source7, source8, source9)) { values ->
        @Suppress("UNCHECKED_CAST")
        mapper(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4, values[4] as T5, values[5] as T6, values[6] as T7, values[7] as T8, values[8] as T9)
    }

    fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T>
}
