package org.modelix.editor

import kotlinx.browser.document
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.tabIndex
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt

class JsEditorComponent(rootCellCreator: () -> Cell) : EditorComponent(rootCellCreator) {

    private var containerElement: HTMLElement = document.create.div("js-editor-component") {
        tabIndex = "-1" // allows setting keyboard focus
    }
    private var selectionView: SelectionView<*>? = null

    init {
        containerElement.addEventListener("click", { event: Event ->
            (event as? MouseEvent)?.let { processClick(it) }
        })
        containerElement.addEventListener("keydown", { event: Event ->
            (event as? KeyboardEvent)?.let { if (processKeyDown(it.convert())) event.preventDefault() }
        })
    }

    fun getHtmlElement(): HTMLElement = containerElement

    fun getMainLayer(): HTMLElement? {
        return containerElement.descendants().filterIsInstance<HTMLElement>().find { it.classList.contains(MAIN_LAYER_CLASS_NAME) }
    }

    override fun update() {
        super.update()
        updateSelectionView()
        updateHtml()
        selectionView?.updateBounds()
    }

    private fun updateSelectionView() {
        if (selectionView?.selection != getSelection()) {
            selectionView = when (val selection = getSelection()) {
                is CaretSelection -> JSCaretSelectionView(selection, this)
                else -> null
            }
        }
    }

    fun updateHtml() {
        val oldEditorElement = this.generatedHtml
        this.generatedHtml = null
        val newEditorElement = IncrementalJSDOMBuilder(document, oldEditorElement).produce(this)()
        if (newEditorElement != oldEditorElement) {
            oldEditorElement?.remove()
            containerElement.append(newEditorElement)
        }
    }

    fun processClick(event: MouseEvent): Boolean {
        val target = event.target ?: return false
        val htmlElement = target as? HTMLElement
        val layoutable = htmlElement?.generatedBy as? LayoutableCell ?: return false
        val text = htmlElement.innerText
        val cellAbsoluteBounds = htmlElement.getAbsoluteBounds()
        val absoluteClickX = event.getAbsolutePositionX()
        val relativeClickX = absoluteClickX - cellAbsoluteBounds.x
        val characterWidth = cellAbsoluteBounds.width / text.length
        val caretPos = (relativeClickX / characterWidth).roundToInt()
        changeSelection(CaretSelection(layoutable, caretPos))
        return true
    }
}

