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
        val body: ()->Unit = {
            textLayoutHandlers.forEach { it(buffer) }
            children.forEach { it.layoutText(buffer) }
        }
        if (properties[CommonCellProperties.indentChildren]) {
            buffer.withIndent(body)
        } else {
            body()
        }
    }
}

class CellProperties {
    private val properties: MutableMap<CellPropertyKey<*>, Any?> = HashMap()
    operator fun <T> get(key: CellPropertyKey<T>): T {
        return if (properties.containsKey(key)) properties[key] as T else key.defaultValue
    }

    operator fun <T> set(key: CellPropertyKey<T>, value: T) {
        properties[key] = value
    }

    fun copy(): CellProperties {
        return CellProperties().also { it.addAll(this) }
    }

    fun addAll(from: CellProperties) {
        properties += from.properties
    }
}

class CellPropertyKey<E>(val name: String, val defaultValue: E)

enum class ECellLayout {
    VERTICAL,
    HORIZONTAL;

    companion object {
        val Key = CellPropertyKey<ECellLayout>("layout", HORIZONTAL)
    }
}

object CommonCellProperties {
    val indentChildren = CellPropertyKey<Boolean>("indentChildren", false)
}

interface ICellAction {

}

class TextCell(val text: String, val placeholderText: String): Cell() {
    override fun toString(): String {
        return if (children.isEmpty())
            text.ifEmpty { placeholderText }
        else """$text<${children}>"""
    }

    override fun layoutText(buffer: LayoutedText) {
        buffer.append(toString())
    }
}
