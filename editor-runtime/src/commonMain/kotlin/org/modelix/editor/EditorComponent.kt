package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div

open class EditorComponent(private val rootCellCreator: ()->Cell) : IProducesHtml {

    private var rootCell: Cell = rootCellCreator().also { it.editorComponent = this }
    private var selection: Selection? = null

    fun updateRootCell() {
        val oldRootCell = rootCell
        val newRootCell = rootCellCreator()
        if (oldRootCell !== newRootCell) {
            oldRootCell.editorComponent = null
            newRootCell.editorComponent = this
            rootCell = newRootCell
        }
    }

    open fun update() {
        updateRootCell()
        updateSelection()
    }

    fun getRootCell() = rootCell

    private fun updateSelection() {
        if (selection?.isValid() != true) {
            selection = null
        }
    }

    open fun changeSelection(newSelection: Selection) {
        selection = newSelection
        update()
    }

    fun getSelection(): Selection? = selection

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

    open fun processKeyDown(event: JSKeyboardEvent): Boolean {
        val selection = this.selection ?: return false
        return selection.processKeyDown(event)
    }

    companion object {
        val MAIN_LAYER_CLASS_NAME = "main-layer"
    }
}
