package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div

class CaretSelection(val cell: Cell, val start: Int, val end: Int) : Selection() {
    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("caret") {

        }
    }
}