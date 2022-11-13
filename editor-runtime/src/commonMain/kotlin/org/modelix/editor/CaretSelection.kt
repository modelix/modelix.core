package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.style

class CaretSelection(val cell: Cell, val start: Int, val end: Int, val css: String? = null) : Selection() {
    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("caret own") {
            val textLength = (cell.data as? TextCellData)?.getVisibleText(cell)?.length ?: 0
            if (end == 0) {
                classes += "leftend"
            } else if (end == textLength) {
                classes += "rightend"
            }
            css?.let { style = it }
        }
    }
}