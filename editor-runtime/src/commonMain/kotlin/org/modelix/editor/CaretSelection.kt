package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlin.math.max
import kotlin.math.min

class CaretSelection(val layoutable: LayoutableCell, val start: Int, val end: Int) : Selection() {
    constructor(cell: LayoutableCell, pos: Int) : this(cell, pos, pos)

    override fun isValid(): Boolean {
        val editor = getEditor() ?: return false
        val visibleText = editor.getRootCell().layout
        val ownText = layoutable.getLine()?.getText()
        return visibleText === ownText
    }

    private fun reResolveLayoutable(editor: EditorComponent): LayoutableCell? {
        return layoutable.cell.data.cellReferences.asSequence()
            .mapNotNull { editor.resolveCell(it).firstOrNull() }
            .firstOrNull()?.layoutable()
    }

    override fun update(editor: EditorComponent): Selection? {
        val newLayoutable = reResolveLayoutable(editor) ?: return null
        val textLength = newLayoutable.getLength()
        return CaretSelection(newLayoutable, start.coerceAtMost(textLength), end.coerceAtMost(textLength))
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
        val knownKey = event.knownKey
        when (knownKey) {
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
            KnownKeys.Delete, KnownKeys.Backspace -> {
                if (start == end) {
                    val posToDelete = when (knownKey) {
                        KnownKeys.Delete -> end
                        KnownKeys.Backspace -> (end - 1)
                        else -> throw RuntimeException("Cannot happen")
                    }
                    val legalRange = 0 until (layoutable.cell.getSelectableText()?.length ?: 0)
                    if (legalRange.contains(posToDelete)) {
                        replaceText(posToDelete .. posToDelete, "", editor)
                    }
                } else {
                    replaceText(min(start, end) until max(start, end), "", editor)
                }
            }
            else -> {
                val typedText = event.typedText
                if (!typedText.isNullOrEmpty()) {
                    if (typedText == " " && event.modifiers == Modifiers.CTRL) {
                        triggerCodeCompletion()
                    } else {
                        replaceText(min(start, end) until max(start, end), typedText, editor)
                    }
                }
            }
        }

        return true
    }

    fun triggerCodeCompletion() {
        val editor = getEditor() ?: throw IllegalStateException("Not attached to any editor")
        val actionProviders = layoutable.cell.getSubstituteActions().toList()
        editor.showCodeCompletionMenu(actionProviders)
    }

    private fun replaceText(range: IntRange, replacement: String, editor: EditorComponent) {
        val actions = layoutable.cell.centerAlignedHierarchy().mapNotNull { it.getProperty(CellActionProperties.replaceText) }
        for (action in actions) {
            if (action.replaceText(editor, range, replacement)) {
                editor.selectAfterUpdate {
                    reResolveLayoutable(editor)?.let { CaretSelection(it, range.first + replacement.length) }
                }
                break
            }
        }
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