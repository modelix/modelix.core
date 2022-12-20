package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div
import org.modelix.incremental.IncrementalIndex

open class EditorComponent(val engine: EditorEngine?, private val rootCellCreator: ()->Cell) : IProducesHtml {

    private var selection: Selection? = null
    private val cellIndex: IncrementalIndex<CellReference, Cell> = IncrementalIndex()
    private var selectionUpdater: (() -> Selection?)? = null
    protected var codeCompletionMenu: CodeCompletionMenu? = null
    private var rootCell: Cell = rootCellCreator().also {
        it.editorComponent = this
        cellIndex.update(it.referencesIndexList)
    }

    fun selectAfterUpdate(newSelection: () -> Selection?) {
        selectionUpdater = newSelection
    }

    fun resolveCell(reference: CellReference): List<Cell> = cellIndex.lookup(reference)

    private fun updateRootCell() {
        val oldRootCell = rootCell
        val newRootCell = rootCellCreator()
        if (oldRootCell !== newRootCell) {
            oldRootCell.editorComponent = null
            newRootCell.editorComponent = this
            rootCell = newRootCell
            cellIndex.update(rootCell.referencesIndexList)
        }
    }

    open fun update() {
        updateRootCell()
        updateSelection()
    }

    fun getRootCell() = rootCell

    private fun updateSelection() {
        val updater = selectionUpdater
        selectionUpdater = null

        selection = updater?.invoke()
            ?: selection?.takeIf { it.isValid() }
            ?: selection?.update(this)
    }

    open fun changeSelection(newSelection: Selection) {
        selection = newSelection
        update()
    }

    fun getSelection(): Selection? = selection

    fun showCodeCompletionMenu(anchor: LayoutableCell, entries: List<ICodeCompletionActionProvider>) {
        codeCompletionMenu = CodeCompletionMenu(this, anchor, entries)
        codeCompletionMenu?.updateActions()
        update()
    }

    fun closeCodeCompletionMenu() {
        codeCompletionMenu = null
        update()
    }

    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("editor") {
            div(MAIN_LAYER_CLASS_NAME) {
                rootCell.layout.let(produceChild)
            }
            div("selection-layer relative-layer") {
                selection?.let(produceChild)
            }
            div("popup-layer relative-layer") {
                codeCompletionMenu?.let(produceChild)
            }
        }
    }

    fun dispose() {

    }

    open fun processKeyDown(event: JSKeyboardEvent): Boolean {
        for (handler in listOfNotNull(codeCompletionMenu, selection)) {
            if (handler.processKeyDown(event)) return true
        }
        return false
    }

    companion object {
        val MAIN_LAYER_CLASS_NAME = "main-layer"
    }
}

interface IKeyboardHandler {
    fun processKeyDown(event: JSKeyboardEvent): Boolean
}