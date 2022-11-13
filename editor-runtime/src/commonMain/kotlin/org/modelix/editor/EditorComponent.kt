package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div

open class EditorComponent(private val rootCellCreator: ()->Cell) : IProducesHtml {

    private var rootCell: Cell = rootCellCreator()
    var selection: Selection? = null

    fun updateRootCell() {
        rootCell = rootCellCreator()
    }

    fun getRootCell() = rootCell

    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("editor") {
            div(MAIN_LAYER_CLASS_NAME) {
                rootCell.layout.let(produceChild)
            }
            div("selection-layer relative-layer") {
                selection?.let(produceChild)
            }
        }
    }

    fun dispose() {

    }

    companion object {
        val MAIN_LAYER_CLASS_NAME = "main-layer"
    }
}