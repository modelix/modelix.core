package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlin.math.max
import kotlin.math.min

class CaretSelection(val cell: LayoutableCell, val start: Int, val end: Int) : Selection() {
    constructor(cell: LayoutableCell, pos: Int) : this(cell, pos, pos)

    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("caret own") {
            val textLength = cell.cell.getVisibleText()?.length ?: 0
            if (end == 0) {
                classes += "leftend"
            } else if (end == textLength) {
                classes += "rightend"
            }
            //css?.let { style = it }
        }
    }

    override fun processKeyDown(event: JSKeyboardEvent): Boolean {
        val editor = getEditor() ?: throw IllegalStateException("Not attached to any editor")
        when (event.knownKey) {
            KnownKeys.ArrowLeft -> {
                if (end > 0) {
                    editor.changeSelection(CaretSelection(cell, end - 1))
                } else {
                    val previous = cell.getSiblingsInText(next = false)
                        .filterIsInstance<LayoutableCell>()
                        .find { it.cell.getSelectableText() != null }
                    if (previous != null) {
                        editor.changeSelection(CaretSelection(previous, previous.cell.getSelectableText()!!.length))
                    }
                }
            }
            KnownKeys.ArrowRight -> {
                if (end < (cell.cell.getSelectableText()?.length ?: 0)) {
                    editor.changeSelection(CaretSelection(cell, end + 1))
                } else {
                    val next = cell.getSiblingsInText(next = true)
                        .filterIsInstance<LayoutableCell>()
                        .find { it.cell.getSelectableText() != null }
                    if (next != null) {
                        editor.changeSelection(CaretSelection(next, 0))
                    }
                }
            }
            else -> {
                val typedText = event.typedText
                if (!typedText.isNullOrEmpty()) {
                    for (action in cell.cell.data.actions.filterIsInstance<ITextChangeAction>()) {
                        val range = min(start, end) until max(start, end)
                        if (action.replaceText(range, typedText)) break
                    }
                }
            }
        }

        return true
    }

    fun getEditor(): EditorComponent? = cell.cell.editorComponent

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CaretSelection

        if (cell != other.cell) return false
        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cell.hashCode()
        result = 31 * result + start
        result = 31 * result + end
        return result
    }

    override fun toString(): String {
        val text = cell.toText()
        return text.substring(0 until end) + "|" + text.substring(end)
    }
}