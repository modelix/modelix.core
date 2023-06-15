package org.modelix.modelql.streams

interface IConsumer<in E> {
    fun onSubscribe(subscription: ISubscription)
    fun onNext(element: E)
    fun onComplete()
    fun onError(ex: Throwable)
}

interface IProducer<out E> {
    fun subscribe(consumer: IConsumer<E>)
}

interface IMono<E> : IProducer<E>
interface IFlux<E> : IProducer<E>

interface ISubscription {
    fun request(n: Long)
    fun cancel()
}

interface IProcessor<T, R> : IConsumer<T>, IProducer<R>