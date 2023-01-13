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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class JsEditorComponent(engine: EditorEngine, rootCellCreator: (EditorState) -> Cell) : EditorComponent(engine, rootCellCreator), IProducesHtml {

    private var containerElement: HTMLElement = document.create.div("js-editor-component") {
        tabIndex = "-1" // allows setting keyboard focus
    }
    private var selectionView: SelectionView<*>? = null
    private var highlightedLine: HTMLElement? = null
    private var highlightedCell: HTMLElement? = null

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
        selectionView?.update()
        codeCompletionMenu?.let { JSCodeCompletionMenuUI(it, this).updateBounds() }
    }

    private fun updateSelectionView() {
        if (selectionView?.selection != getSelection()) {
            selectionView = when (val selection = getSelection()) {
                is CaretSelection -> JSCaretSelectionView(selection, this)
                is CellSelection -> JSCellSelectionView(selection, this)
                else -> null
            }
        }
    }

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.div("editor") {
            div(MAIN_LAYER_CLASS_NAME) {
                produceChild(getRootCell().layout)
            }
            div("selection-layer relative-layer") {
                produceChild(selectionView)
            }
            div("popup-layer relative-layer") {
                produceChild(codeCompletionMenu)
            }
        }
    }

    override fun isHtmlOutputValid(): Boolean = false

    fun updateHtml() {
        val oldEditorElement = GeneratedHtmlMap.getOutput(this)
        val newEditorElement = IncrementalJSDOMBuilder(document, oldEditorElement).produce(this)()
        if (newEditorElement != oldEditorElement) {
            oldEditorElement?.remove()
            containerElement.append(newEditorElement)
        }

        val selectedLayoutable = (getSelection() as? CaretSelection)?.layoutable

        val newHighlightedLine = selectedLayoutable?.getLine()?.let { GeneratedHtmlMap.getOutput(it) }
        if (newHighlightedLine != highlightedLine) {
            highlightedLine?.classList?.remove("highlighted")
        }
        newHighlightedLine?.classList?.add("highlighted")
        highlightedLine = newHighlightedLine

        val newHighlightedCell = selectedLayoutable?.let { GeneratedHtmlMap.getOutput(it) }
        if (newHighlightedCell != highlightedCell) {
            highlightedCell?.classList?.remove("highlighted-cell")
        }
        newHighlightedCell?.classList?.add("highlighted-cell")
        highlightedCell = newHighlightedCell
    }

    fun processClick(event: MouseEvent): Boolean {
        val absoluteClickX = event.pageX
        val targets = document.elementsFromPoint(event.clientX.toDouble(), event.clientY.toDouble())
        for (target in targets) {
            val htmlElement = target as? HTMLElement
            val producer: IProducesHtml = htmlElement?.let { GeneratedHtmlMap.getProducer(it) } ?: continue
            when (producer) {
                is LayoutableCell -> {
                    val layoutable = producer as? LayoutableCell ?: continue
                    val text = htmlElement.innerText
                    val cellAbsoluteBounds = htmlElement.getAbsoluteInnerBounds()
                    val relativeClickX = absoluteClickX - cellAbsoluteBounds.x
                    val characterWidth = cellAbsoluteBounds.width / text.length
                    val caretPos = (relativeClickX / characterWidth).roundToInt()
                        .coerceAtMost(layoutable.cell.getMaxCaretPos())
                    changeSelection(CaretSelection(layoutable, caretPos))
                    return true
                }
                is Layoutable -> {
                    if (selectClosestInLine(producer.getLine() ?: continue, absoluteClickX)) return true
                }
                is TextLine -> {
                    if (selectClosestInLine(producer, absoluteClickX)) return true
                }
                else -> continue
            }
        }
        return false
    }

    private fun selectClosestInLine(line: TextLine, absoluteClickX: Double): Boolean {
        val words = line.words.filterIsInstance<LayoutableCell>()
        val closest = words.map { it to GeneratedHtmlMap.getOutput(it)!! }.minByOrNull {
            min(
                abs(absoluteClickX - it.second.getAbsoluteBounds().minX()),
                abs(absoluteClickX - it.second.getAbsoluteBounds().maxX())
            )
        } ?: return false
        val caretPos = if (absoluteClickX <= closest.second.getAbsoluteBounds().minX()) 0 else closest.first.getLength()
        changeSelection(CaretSelection(closest.first, caretPos))
        return true
    }
}

