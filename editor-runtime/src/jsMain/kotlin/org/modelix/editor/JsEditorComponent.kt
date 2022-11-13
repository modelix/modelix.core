package org.modelix.editor

import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt

class JsEditorComponent(rootCellCreator: () -> Cell) : EditorComponent(rootCellCreator) {

    private var containerElement: HTMLElement = document.create.div("js-editor-component") {}

    init {
        containerElement.addEventListener("click", { event_: Event ->
            val event = event_ as? MouseEvent ?: return@addEventListener
            val target = event.target ?: return@addEventListener
            val htmlElement = target as? HTMLElement
            val layoutable = htmlElement?.generatedBy as? LayoutableCell ?: return@addEventListener
            layoutable.getLength()
            val text = htmlElement.innerText
            val cellAbsoluteBounds = htmlElement.getAbsoluteBounds()
            val cellRelativeBounds = cellAbsoluteBounds.relativeTo(getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS)
            val absoluteClickX = event.getAbsolutePositionX()
            val relativeClickX = absoluteClickX - cellAbsoluteBounds.x
            val characterWidth = cellAbsoluteBounds.width / text.length
            val caretPos = (relativeClickX / characterWidth).roundToInt()
            val caretX = cellAbsoluteBounds.x + caretPos * characterWidth
            val cell = layoutable.cell
            val leftEnd = caretPos == 0
            val rightEnd = caretPos == text.length
            val caretOffsetX = if (rightEnd) -4 else -1
            val caretOffsetY = if (leftEnd || rightEnd) -1 else 0
            val caretCss = "height: ${cellRelativeBounds.height}px; left: ${caretX + caretOffsetX}px; top: ${cellRelativeBounds.y + caretOffsetY}px"
            selection = CaretSelection(cell, caretPos, caretPos, caretCss)
            console.log("click on cell", cell, selection)
            updateHtml()
        })
    }

    fun getHtmlElement(): HTMLElement = containerElement

    fun getMainLayer(): HTMLElement? {
        return containerElement.descendants().filterIsInstance<HTMLElement>().find { it.classList.contains(MAIN_LAYER_CLASS_NAME) }
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
}

