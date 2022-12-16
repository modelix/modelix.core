package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlin.math.max
import kotlin.math.min

class CaretSelection(val layoutable: LayoutableCell, val start: Int, val end: Int) : Selection() {
    constructor(cell: LayoutableCell, pos: Int) : this(cell, pos, pos)

    override fun isValid(): Boolean {
        val editor = getEditor()
        return editor != null && editor.getRootCell().layout === layoutable.getLine()?.getText()
    }

    override fun <T> toHtml(consumer: TagConsumer<T>, produceChild: (IProducesHtml) -> T) {
        consumer.div("caret own") {
            val textLength = layoutable.cell.getVisibleText()?.length ?: 0
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
                    editor.changeSelection(CaretSelection(layoutable, end - 1))
                } else {
                    val previous = layoutable.getSiblingsInText(next = false)
                        .filterIsInstance<LayoutableCell>()
                        .find { it.cell.getSelectableText() != null }
                    if (previous != null) {
                        editor.changeSelection(CaretSelection(previous, previous.cell.getSelectableText()!!.length))
                    }
                }
            }
            KnownKeys.ArrowRight -> {
                if (end < (layoutable.cell.getSelectableText()?.length ?: 0)) {
                    editor.changeSelection(CaretSelection(layoutable, end + 1))
                } else {
                    val next = layoutable.getSiblingsInText(next = true)
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
                    for (action in layoutable.cell.data.actions.filterIsInstance<ITextChangeAction>()) {
                        val range = min(start, end) until max(start, end)
                        if (action.replaceText(range, typedText)) break
                    }
                }
            }
        }

        return true
    }

    fun getEditor(): EditorComponent? = layoutable.cell.editorComponent

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CaretSelection

        if (layoutable != other.layoutable) return false
        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int {
        var result = layoutable.hashCode()
        result = 31 * result + start
        result = 31 * result + end
        return result
    }

    override fun toString(): String {
        val text = layoutable.toText()
        return text.substring(0 until end) + "|" + text.substring(end)
    }
}