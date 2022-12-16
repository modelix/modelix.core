package org.modelix.editor

interface ICellAction {

}

interface ITextChangeAction: ICellAction {
    fun replaceText(editor: EditorComponent, range: IntRange, replacement: String): Boolean
}