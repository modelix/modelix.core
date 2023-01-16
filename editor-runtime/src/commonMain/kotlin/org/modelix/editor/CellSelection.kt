/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.editor

data class CellSelection(val cell: Cell, val directionLeft: Boolean, val previousSelection: Selection?): Selection() {
    fun getEditor(): EditorComponent? = cell.editorComponent

    override fun isValid(): Boolean {
        return getEditor() != null
    }

    override fun update(editor: EditorComponent): Selection? {
        return cell.data.cellReferences.asSequence()
            .flatMap { editor.resolveCell(it) }
            .map { CellSelection(it, directionLeft, previousSelection?.update(editor)) }
            .firstOrNull()
    }

    override fun processKeyDown(event: JSKeyboardEvent): Boolean {
        val editor = getEditor() ?: throw IllegalStateException("Not attached to any editor")
        when (event.knownKey) {
            KnownKeys.ArrowUp -> {
                if (event.modifiers.meta) {
                    cell.ancestors().firstOrNull { it.getProperty(CommonCellProperties.selectable) }
                        ?.let { editor.changeSelection(CellSelection(it, directionLeft, this)) }
                } else {
                    unwrapCaretSelection()?.selectNextPreviousLine(false)
                }
            }
            KnownKeys.ArrowDown -> {
                if (event.modifiers == Modifiers.META && previousSelection != null) {
                    editor.changeSelection(previousSelection)
                } else {
                    unwrapCaretSelection()?.selectNextPreviousLine(true)
                }
            }
            KnownKeys.ArrowLeft, KnownKeys.ArrowRight -> {
                if (event.modifiers == Modifiers.SHIFT) {
                    val isLeft = event.knownKey == KnownKeys.ArrowLeft
                    if (isLeft == directionLeft) {
                        cell.ancestors().firstOrNull { it.isSelectable() }
                            ?.let { editor.changeSelection(CellSelection(it, directionLeft, this)) }
                    } else {
                        previousSelection?.let { editor.changeSelection(it) }
                    }
                } else {
                    val caretSelection = unwrapCaretSelection()
                    if (caretSelection != null) {
                        editor.changeSelection(CaretSelection(caretSelection.layoutable, caretSelection.start))
                    } else {
                        val tabTargets = cell.descendantsAndSelf().filter { it.isTabTarget() }
                        if (event.knownKey == KnownKeys.ArrowLeft) {
                            tabTargets.firstOrNull()?.layoutable()
                                ?.let { editor.changeSelection(CaretSelection(it, 0)) }
                        } else {
                            tabTargets.lastOrNull()?.layoutable()
                                ?.let { editor.changeSelection(CaretSelection(it, it.cell.getSelectableText()?.length ?: 0)) }
                        }
                    }
                }
            }
            else -> {
                val typedText = event.typedText
                if (!typedText.isNullOrEmpty()) {
                    val anchor = getLayoutables().filterIsInstance<LayoutableCell>().firstOrNull()
                    if (anchor != null) {
                        val actionProviders = cell.getSubstituteActions().toList()
                        if (typedText == " " && event.modifiers == Modifiers.CTRL) {
                            editor.showCodeCompletionMenu(
                                anchor = anchor,
                                position = CompletionPosition.CENTER,
                                entries = actionProviders,
                                pattern = "",
                                caretPosition = 0
                            )
                        } else {
                            editor.showCodeCompletionMenu(
                                anchor = anchor,
                                position = CompletionPosition.CENTER,
                                entries = actionProviders,
                                pattern = typedText,
                                caretPosition = typedText.length
                            )
                        }
                    }
                }
            }
        }

        return true
    }

    private fun unwrapCaretSelection(): CaretSelection? {
        return generateSequence<Selection>(this) { (it as? CellSelection)?.previousSelection }
            .lastOrNull() as? CaretSelection
    }

    fun getLayoutables(): List<Layoutable> {
        val editor = getEditor() ?: return emptyList()
        val rootText = editor.getRootCell().layout
        return cell.layout.lines.asSequence().flatMap { it.words }
            .filter { it.getLine()?.getText() === rootText }.toList()
    }
}