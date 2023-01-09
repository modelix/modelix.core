package org.modelix.editor

import kotlinx.html.Tag
import kotlinx.html.TagConsumer

interface IProducesHtml {
    fun isHtmlOutputValid(): Boolean = true
    fun <T> produceHtml(consumer: TagConsumer<T>)
}

interface IIncrementalTagConsumer<E> : TagConsumer<E> {
    fun produce(producer: IProducesHtml): ()->E
}

fun Tag.produceChild(child: IProducesHtml?) {
    consumer.produceChild(child)
}

fun <T> TagConsumer<T>.produceChild(child: IProducesHtml?) {
    if (child == null) return
    if (this is IIncrementalTagConsumer) {
        produce(child)
    } else {
        child.produceHtml(this)
    }
}

fun <T> IProducesHtml.toHtml(consumer: TagConsumer<T>): T {
    return if (consumer is IIncrementalTagConsumer) {
        consumer.produce(this)()
    } else {
        produceHtml(consumer)
        consumer.finalize()
    }
}