package org.modelix.editor

import kotlinx.html.TagConsumer

interface IProducesHtml {
    fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml)->T)
}

interface IIncrementalTagConsumer<E> : TagConsumer<E> {
    fun produce(producer: IProducesHtml): ()->E
}

fun <T> IProducesHtml.toHtml(consumer: TagConsumer<T>): T {
    return if (consumer is IIncrementalTagConsumer) {
        consumer.produce(this)()
    } else {
        toHtml(consumer) { child -> child.toHtml(consumer) }
        consumer.finalize()
    }
}