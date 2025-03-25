package org.modelix.streams

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.modelix.kotlin.utils.ContextValue

interface IStreamBuilder : IStreamExecutorProvider {
    fun zero(): IStream.Zero
    fun <T> empty(): IStream.ZeroOrOne<T>
    fun <T> of(element: T): IStream.One<T>
    fun <T> of(vararg elements: T): IStream.Many<T> = many(elements)
    fun <T : Any> ofNotNull(element: T?): IStream.ZeroOrOne<T> = if (element == null) empty() else of(element)

    fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T>

    fun <T> many(elements: Iterable<T>): IStream.Many<T> = many(elements.asSequence())
    fun <T> many(elements: Array<T>): IStream.Many<T> = many(elements.asSequence())
    fun many(elements: LongArray): IStream.Many<Long> = many(elements.asSequence())
    fun <T> many(elements: Sequence<T>): IStream.Many<T>

    fun <T> fromFlow(flow: Flow<T>): IStream.Many<T>

    fun <T, R> zip(input: Iterable<IStream.Many<T>>, mapper: (List<T>) -> R): IStream.Many<R>
    fun <T, R> zip(input: Iterable<IStream.One<T>>, mapper: (List<T>) -> R): IStream.One<R>
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

open class IndirectStreamBuilder(private val provider: () -> IStreamBuilder) : IStreamBuilder {
    override fun getStreamExecutor(): IStreamExecutor = provider().getStreamExecutor()
    override fun zero(): IStream.Zero = provider().zero()
    override fun <T> empty(): IStream.ZeroOrOne<T> = provider().empty()
    override fun <T> of(element: T): IStream.One<T> = provider().of(element)
    override fun <T> of(vararg elements: T): IStream.Many<T> = provider().of(*elements)
    override fun <T> deferZeroOrOne(supplier: () -> IStream.ZeroOrOne<T>): IStream.ZeroOrOne<T> = provider().deferZeroOrOne(supplier)
    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = provider().many(elements)
    override fun <T, R> zip(input: Iterable<IStream.Many<T>>, mapper: (List<T>) -> R): IStream.Many<R> =
        provider().zip(input, mapper)
    override fun <T, R> zip(input: Iterable<IStream.One<T>>, mapper: (List<T>) -> R): IStream.One<R> =
        provider().zip(input, mapper)
    override fun <T> singleFromCoroutine(block: suspend CoroutineScope.() -> T): IStream.One<T> =
        provider().singleFromCoroutine(block)
    override fun <T> many(elements: Iterable<T>): IStream.Many<T> = provider().many(elements)
    override fun <T> many(elements: Array<T>): IStream.Many<T> = provider().many(elements)
    override fun many(elements: LongArray): IStream.Many<Long> = provider().many(elements)
    override fun <T1, T2, R> zip(
        source1: IStream.One<T1>,
        source2: IStream.One<T2>,
        mapper: (T1, T2) -> R,
    ): IStream.One<R> = provider().zip(source1, source2, mapper)
    override fun <T : Any> ofNotNull(element: T?): IStream.ZeroOrOne<T> = provider().ofNotNull(element)
    override fun <T> fromFlow(flow: Flow<T>): IStream.Many<T> = provider().fromFlow(flow)
}

class ContextStreamBuilder(
    val contextValue: ContextValue<IStreamBuilder> = ContextValue(DeferredStreamBuilder()),
) : IndirectStreamBuilder({
    checkNotNull(contextValue.getValueOrNull()) { "No stream builder available in the current context" }
}) {
    companion object {
        val globalInstance = ContextStreamBuilder()
    }
}
