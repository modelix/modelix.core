package org.modelix.editor

import org.modelix.metamodel.ITypedNode

open class CellData : Freezable(), ILocalOrChildNodeCell {
    val cellReferences: MutableList<CellReference> = ArrayList()
    val children: MutableList<ILocalOrChildNodeCell> = ArrayList()
    val properties = CellProperties()

    fun addChild(child: ILocalOrChildNodeCell) {
        children.add(child)
    }

    open fun layout(buffer: TextLayouter, cell: Cell) {
        val body: ()->Unit = {
            if (properties[CommonCellProperties.onNewLine]) buffer.onNewLine()
            if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
            cell.getChildren().forEach { buffer.append(it.layout) }
            if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
        }
        if (properties[CommonCellProperties.indentChildren]) {
            buffer.withIndent(body)
        } else {
            body()
        }
    }

    open fun cellToString(cell: Cell) = "[${cell.getChildren().joinToString(" ")}]"

    open fun isVisible(): Boolean = false
}

fun Cell.isVisible() = data.isVisible()

interface ILocalOrChildNodeCell {

}

class ChildDataReference(val childNode: ITypedNode) : ILocalOrChildNodeCell {

}

class TextCellData(val text: String, private val placeholderText: String = "") : CellData() {
    fun getVisibleText(cell: Cell): String {
        return if (cell.getChildren().isEmpty()) {
            text.ifEmpty { placeholderText }
        } else {
            """$text<${cell.getChildren()}>"""
        }
    }

    override fun layout(buffer: TextLayouter, cell: Cell) {
        if (properties[CommonCellProperties.onNewLine]) buffer.onNewLine()
        if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
        buffer.append(LayoutableCell(cell))
        if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
    }

    override fun cellToString(cell: Cell) = getVisibleText(cell)

    override fun isVisible(): Boolean = true
}
