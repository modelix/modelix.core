package org.modelix.editor

open class Cell {
    val children: MutableList<Cell> = ArrayList()
    val actions: MutableList<ICellAction> = ArrayList()
    val properties = CellProperties()
    val textLayoutHandlers: MutableList<(LayoutedText)->Unit> = ArrayList()

    override fun toString(): String {
        return children.toString()
    }

    open fun layoutText(buffer: LayoutedText) {
        textLayoutHandlers.forEach { it(buffer) }
        children.forEach { it.layoutText(buffer) }
    }
}

class CellProperties {
    val properties: MutableMap<CellPropertyKey<*>, Any?> = HashMap<CellPropertyKey<*>, Any?>()
    operator fun <T> get(key: CellPropertyKey<T>): T {
        return properties[key] as T
    }

    operator fun <T> set(key: CellPropertyKey<T>, value: T) {
        properties[key] = value
    }
}

class CellPropertyKey<E>(val name: String)

interface ICellAction {

}

class TextCell(val text: String, val placeholderText: String): Cell() {
    override fun toString(): String {
        return if (children.isEmpty())
            text
        else """$text<${children}>"""
    }

    override fun layoutText(buffer: LayoutedText) {
        buffer.append(text)
    }
}
