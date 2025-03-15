package org.modelix.streams

// class KotlinFlowsStreamBuilder : IStreamBuilder {
//    override fun <T> one(element: T): IStream.One<T> = One(flowOf(element))
//    override fun <T> zeroOrOne(): IStream.ZeroOrOne<T> = ZeroOrOne(emptyFlow())
//    override fun <T> zeroOrOne(element: T): IStream.ZeroOrOne<T> = ZeroOrOne(flowOf(element))
//    override fun <T> many(): IStream.Many<T> = Many(emptyFlow())
//    override fun <T> many(elements: Iterable<T>): IStream.Many<T> = Many(elements.asFlow())
//    override fun <T> many(elements: Sequence<T>): IStream.Many<T> = Many(elements.asFlow())
//    override fun <T> oneOrMany(vararg elements: T): IStream.OneOrMany<T> = OneOrMany(elements.asFlow())
//    override fun <T> oneOrMany(elements: Iterable<T>): IStream.OneOrMany<T> = OneOrMany(elements.asFlow())
//    override fun <T> oneOrMany(elements: Sequence<T>): IStream.OneOrMany<T> = OneOrMany(elements.asFlow())
//
//    abstract class Stream<E>(val wrapped: Flow<E>) : IStream<E> {
//        override fun asFlow(): Flow<E> = wrapped
//    }
//
//    class One<E>(wrapped: Flow<E>): Stream<E>(wrapped), IStream.One<E>
//    class OneOrMany<E>(wrapped: Flow<E>): Stream<E>(wrapped), IStream.OneOrMany<E>
//    class ZeroOrOne<E>(wrapped: Flow<E>): Stream<E>(wrapped), IStream.ZeroOrOne<E>
//    class Many<E>(wrapped: Flow<E>): Stream<E>(wrapped), IStream.Many<E>
// }
