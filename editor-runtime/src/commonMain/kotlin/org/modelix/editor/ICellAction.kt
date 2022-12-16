package org.modelix.editor

interface ICellAction {

}

interface ITextChangeAction: ICellAction {
    fun replaceText(range: IntRange, replacement: String): Boolean
}