package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div

open class EditorComponent : IProducesHtml {

    var rootCell: Cell? = null
    var selection: Selection? = null

    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("editor") {
            div("main-layer") {
                rootCell?.layout?.let(produceChild)
            }
            div("selection-layer") {
                selection?.let(produceChild)
            }
        }
    }
}