package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

class CodeCompletionMenu(
    val editor: EditorComponent,
    val anchor: LayoutableCell,
    val completionPosition: CompletionPosition,
    val providers: List<ICodeCompletionActionProvider>,
    initialPattern: String = "",
    initialCaretPosition: Int? = null,
) : IProducesHtml, IKeyboardHandler {
    val patternEditor = PatternEditor(initialPattern, initialCaretPosition)
    private var selectedIndex: Int = 0
    private var entries: List<ICodeCompletionAction> = emptyList()

    override fun isHtmlOutputValid(): Boolean = false

    fun updateActions() {
        val parameters = parameters()
        entries = providers.flatMap { it.getActions(parameters) }
            .filter { it.isApplicable(parameters) }
            .filter {
                val matchingText = it.getMatchingText(parameters)
                matchingText.isNotEmpty() && matchingText.startsWith(parameters.pattern)
            }
            .sortedBy { it.getMatchingText(parameters) }
    }

    private fun parameters() = CodeCompletionParameters(editor, patternEditor.getTextBeforeCaret())

    fun selectNext() {
        selectedIndex++
        if (selectedIndex >= entries.size) selectedIndex = 0
    }

    fun selectPrevious() {
        selectedIndex--
        if (selectedIndex < 0) selectedIndex = (entries.size - 1).coerceAtLeast(0)
    }

    fun getSelectedEntry(): ICodeCompletionAction? = entries.getOrNull(selectedIndex)

    override fun processKeyDown(event: JSKeyboardEvent): Boolean {
        when (event.knownKey) {
            KnownKeys.ArrowUp -> selectPrevious()
            KnownKeys.ArrowDown -> selectNext()
            KnownKeys.ArrowLeft -> patternEditor.moveCaret(-1)
            KnownKeys.ArrowRight -> patternEditor.moveCaret(1)
            KnownKeys.Escape -> editor.closeCodeCompletionMenu()
            KnownKeys.Enter -> {
                getSelectedEntry()?.execute()
                editor.closeCodeCompletionMenu()
            }
            KnownKeys.Backspace -> patternEditor.deleteText(true)
            KnownKeys.Delete -> patternEditor.deleteText(false)
            else -> {
                if (!event.typedText.isNullOrEmpty()) {
                    patternEditor.insertText(event.typedText)
                } else {
                    return false
                }
            }
        }
        editor.update()
        return true
    }

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.div("ccmenu-container") {
            produceChild(patternEditor)
            div("ccmenu") {
                table {
                    val parameters = parameters()
                    entries.forEachIndexed { index, action ->
                        tr("ccSelectedEntry".takeIf { index == selectedIndex }) {
                            td("matchingText") {
                                +action.getMatchingText(parameters)
                            }
                            td("description") {
                                +action.getDescription(parameters)
                            }
                        }
                    }
                    if (entries.isEmpty()) {
                        tr {
                            td {
                                +"No matches found"
                            }
                        }
                    }
                }
            }
        }
    }

    inner class PatternEditor(initialPattern: String, initialCaretPosition: Int?) : IProducesHtml {
        private var patternCell: Cell? = null
        var caretPos: Int = initialCaretPosition ?: initialPattern.length
        var pattern: String = initialPattern

        override fun isHtmlOutputValid(): Boolean = false

        fun getTextBeforeCaret() = pattern.substring(0, caretPos)

        fun deleteText(before: Boolean): Boolean {
            if (before) {
                if (caretPos == 0) return false
                pattern = pattern.removeRange((caretPos - 1) until caretPos)
                caretPos--
            } else {
                if (caretPos == pattern.length) return false
                pattern = pattern.removeRange(caretPos .. caretPos)
            }
            updateActions()
            return true
        }

        fun insertText(text: String) {
            pattern = pattern.replaceRange(caretPos until caretPos, text)
            caretPos += text.length
            updateActions()
        }

        fun moveCaret(delta: Int) {
            caretPos = (caretPos + delta).coerceIn(0..pattern.length)
            updateActions()
        }

        override fun <T> produceHtml(consumer: TagConsumer<T>) {
            consumer.div {
                div("ccmenu-pattern") {
                    +pattern.useNbsp()
                }
                div("caret own") {  }
            }
        }
    }
}

interface ICodeCompletionActionProvider {
    fun getActions(parameters: CodeCompletionParameters): List<ICodeCompletionAction>
}

interface ICodeCompletionAction {
    fun isApplicable(parameters: CodeCompletionParameters): Boolean
    fun getMatchingText(parameters: CodeCompletionParameters): String
    fun getDescription(parameters: CodeCompletionParameters): String
    fun execute()
}

class CodeCompletionActionWithPostprocessor(val action: ICodeCompletionAction, val after: () -> Unit) : ICodeCompletionAction by action {
    override fun execute() {
        action.execute()
        after()
    }
}
class CodeCompletionActionProviderWithPostprocessor(
    val actionProvider: ICodeCompletionActionProvider,
    val after: () -> Unit
) : ICodeCompletionActionProvider {
    override fun getActions(parameters: CodeCompletionParameters): List<ICodeCompletionAction> {
        return actionProvider.getActions(parameters).map { CodeCompletionActionWithPostprocessor(it, after) }
    }
}

fun ICodeCompletionActionProvider.after(body: () -> Unit) = CodeCompletionActionProviderWithPostprocessor(this, body)

class CodeCompletionParameters(val editor: EditorComponent, val pattern: String)

enum class CompletionPosition {
    CENTER,
    LEFT,
    RIGHT
}