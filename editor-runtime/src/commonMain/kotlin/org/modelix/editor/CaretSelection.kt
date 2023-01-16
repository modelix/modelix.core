package org.modelix.editor

import kotlin.math.max
import kotlin.math.min

class CaretSelection(val layoutable: LayoutableCell, val start: Int, val end: Int, val desiredXPosition: Int? = null) : Selection() {
    constructor(cell: LayoutableCell, pos: Int) : this(cell, pos, pos)
    constructor(cell: LayoutableCell, pos: Int, desiredXPosition: Int?) : this(cell, pos, pos, desiredXPosition)

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

    override fun processKeyDown(event: JSKeyboardEvent): Boolean {
        val editor = getEditor() ?: throw IllegalStateException("Not attached to any editor")
        val knownKey = event.knownKey
        when (knownKey) {
            KnownKeys.ArrowLeft -> {
                if (end > 0) {
                    if (event.modifiers.shift) {
                        editor.changeSelection(CaretSelection(layoutable, start, end - 1))
                    } else {
                        editor.changeSelection(CaretSelection(layoutable, end - 1))
                    }
                } else {
                    val previous = layoutable.getSiblingsInText(next = false)
                        .filterIsInstance<LayoutableCell>()
                        .find { it.cell.getSelectableText() != null }
                    if (previous != null) {
                        if (event.modifiers.shift) {
                            val commonAncestor = layoutable.cell.commonAncestor(previous.cell)
                            val selectableAncestor = commonAncestor.ancestors(true).filter { it.isSelectable() }.firstOrNull()
                            selectableAncestor?.let { editor.changeSelection(CellSelection(it, true, this)) }
                        } else {
                            editor.changeSelection(CaretSelection(previous, previous.cell.getMaxCaretPos()))
                        }
                    }
                }
            }
            KnownKeys.ArrowRight -> {
                if (end < (layoutable.cell.getSelectableText()?.length ?: 0)) {
                    if (event.modifiers.shift) {
                        editor.changeSelection(CaretSelection(layoutable, start, end + 1))
                    } else {
                        editor.changeSelection(CaretSelection(layoutable, end + 1))
                    }
                } else {
                    val next = layoutable.getSiblingsInText(next = true)
                        .filterIsInstance<LayoutableCell>()
                        .find { it.cell.getSelectableText() != null }
                    if (next != null) {
                        if (event.modifiers.shift) {
                            val commonAncestor = layoutable.cell.commonAncestor(next.cell)
                            val selectableAncestor = commonAncestor.ancestors(true).filter { it.isSelectable() }.firstOrNull()
                            selectableAncestor?.let { editor.changeSelection(CellSelection(it, false, this)) }
                        } else {
                            editor.changeSelection(CaretSelection(next, 0))
                        }
                    }
                }
            }
            KnownKeys.ArrowDown -> {
                selectNextPreviousLine(true)
            }
            KnownKeys.ArrowUp -> {
                if (event.modifiers.meta) {
                    layoutable.cell.let { editor.changeSelection(CellSelection(it, true, this)) }
                } else {
                    selectNextPreviousLine(false)
                }
            }
            KnownKeys.Tab -> {
                val target = layoutable
                    .getSiblingsInText(!event.modifiers.shift)
                    .filterIsInstance<LayoutableCell>()
                    .firstOrNull { it.cell.isTabTarget() }
                if (target != null) {
                    editor.changeSelection(CaretSelection(target, 0))
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
            KnownKeys.Enter -> {
                var previousLeaf: Cell? = layoutable.cell
                while (previousLeaf != null) {
                    val nextLeaf = previousLeaf.nextLeaf { it.isVisible() }
                    val actions = collectActionsBetween(
                        previousLeaf,
                        nextLeaf
                    ) { cellsFullyBetween, cellsEndingBetween, cellsBeginningBetween ->
                        cellsFullyBetween.map { it.getProperty(CellActionProperties.insert) }
                    }.filter { it.isApplicable() }
                    // TODO resolve conflicts if multiple actions are applicable
                    val action = actions.firstOrNull()
                    if (action != null) {
                        action.execute(editor)
                        break
                    }
                    previousLeaf = nextLeaf
                }
            }
            else -> {
                val typedText = event.typedText
                if (!typedText.isNullOrEmpty()) {
                    if (typedText == " " && event.modifiers == Modifiers.CTRL) {
                        triggerCodeCompletion()
                    } else {
                        processTypedText(typedText, editor)
                    }
                }
            }
        }

        return true
    }

    fun selectNextPreviousLine(next: Boolean) {
        createNextPreviousLineSelection(next, desiredXPosition ?: getAbsoluteX())
            ?.let { getEditor()?.changeSelection(it) }
    }

    private fun processTypedText(typedText: String, editor: EditorComponent) {
        val oldText = layoutable.cell.getSelectableText() ?: ""
        val range = min(start, end) until max(start, end)
        val textLength = oldText.length
        val leftTransform = start == end && end == 0
        val rightTransform = start == end && end == textLength
        if (leftTransform || rightTransform) {
            //if (replaceText(range, typedText, editor, false)) return

            val completionPosition = if (leftTransform) CompletionPosition.LEFT else CompletionPosition.RIGHT
            val providers = (if (completionPosition == CompletionPosition.LEFT) {
                layoutable.cell.getActionsBefore()
            } else {
                layoutable.cell.getActionsAfter()
            }).toList()
            val params = CodeCompletionParameters(editor, typedText)
            val actions = providers.flatMap { it.flattenApplicableActions(params) }
            val matchingActions = actions
                .filter { it.getMatchingText().startsWith(typedText) }
                .applyShadowing()
            if (matchingActions.isNotEmpty()) {
                if (matchingActions.size == 1 && matchingActions.first().getMatchingText() == typedText) {
                    matchingActions.first().execute(editor)
                    return
                }
                editor.showCodeCompletionMenu(
                    anchor = layoutable,
                    position = completionPosition,
                    entries = providers,
                    pattern = typedText,
                    caretPosition = typedText.length
                )
                return
            }
        }
        replaceText(range, typedText, editor)
    }

    fun getAbsoluteX() = layoutable.getX() + end

    fun getTextBeforeCaret() = (layoutable.cell.getSelectableText() ?: "").substring(0, end)

    fun triggerCodeCompletion() {
        val editor = getEditor() ?: throw IllegalStateException("Not attached to any editor")
        val actionProviders = layoutable.cell.getSubstituteActions().toList()
        editor.showCodeCompletionMenu(
            anchor = layoutable,
            position = CompletionPosition.CENTER,
            entries = actionProviders,
            pattern = layoutable.cell.getSelectableText() ?: "",
            caretPosition = end
        )
    }

    private fun replaceText(range: IntRange, replacement: String, editor: EditorComponent): Boolean {
        val oldText = layoutable.cell.getSelectableText() ?: ""
        val newText = oldText.replaceRange(range, replacement)
        val actions = layoutable.cell.centerAlignedHierarchy().mapNotNull { it.getProperty(CellActionProperties.replaceText) }
        for (action in actions) {
            if (action.isValid(newText) && action.replaceText(editor, range, replacement, newText)) {
                editor.selectAfterUpdate {
                    reResolveLayoutable(editor)?.let { CaretSelection(it, range.first + replacement.length) }
                }
                return true
            }
        }
        return false
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

    private fun createNextPreviousLineSelection(next: Boolean, x: Int): CaretSelection? {
        val line: TextLine = layoutable.getLine() ?: return null
        val text: LayoutedText = line.getText() ?: return null
        val lines = text.lines.asSequence()
        val nextPrevLines = if (next) {
            lines.dropWhile { it != line }.drop(1)
        } else {
            lines.takeWhile { it != line }.toList().reversed().asSequence()
        }
        return nextPrevLines.mapNotNull { it.createBestMatchingCaretSelection(x) }.firstOrNull()
    }
}

fun TextLine.createBestMatchingCaretSelection(x: Int): CaretSelection? {
    var currentOffset = 0
    for (layoutable in words) {
        val length = layoutable.getLength()
        val range = currentOffset..(currentOffset + length)
        if (layoutable is LayoutableCell) {
            if (x < range.first) return CaretSelection(layoutable, 0, desiredXPosition = x)
            if (range.contains(x)) return CaretSelection(layoutable, x - range.first, desiredXPosition = x)
        }
        currentOffset += length
    }
    return words.filterIsInstance<LayoutableCell>().lastOrNull()?.let { CaretSelection(it, it.getLength(), desiredXPosition = x) }
}