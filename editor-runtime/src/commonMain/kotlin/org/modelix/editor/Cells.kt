package org.modelix.editor

interface IFreezable {
    fun freeze()
    fun checkNotFrozen()
}

open class Freezable {
    private var frozen: Boolean = false
    open fun freeze() {
        frozen = true
    }

    fun checkNotFrozen() {
        if (frozen) {
            throw IllegalStateException("Cell cannot be modified anymore")
        }
    }
}

open class Cell : Freezable() {
    var parent: Cell? = null
    private val children: MutableList<Cell> = ArrayList()
    val actions: MutableList<ICellAction> = ArrayList()
    val properties = CellProperties()

    override fun freeze() {
        super.freeze()
        properties.freeze()
    }

    override fun toString(): String {
        return children.toString()
    }

    fun addChild(child: Cell) {
        require(child.parent == null) { "$child already has a parent ${child.parent}" }
        children.add(child)
        child.parent = this
    }

    fun removeChild(child: Cell) {
        require(child.parent == this) { "$child is not a child of $this" }
        children.remove(child)
        child.parent = null
    }

    fun getChildren(): List<Cell> = children

    open fun layout(buffer: LayoutedCells) {
        val body: ()->Unit = {
            if (properties[CommonCellProperties.onNewLine]) buffer.onNewLine()
            if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
            children.forEach { it.layout(buffer) }
            if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
        }
        if (properties[CommonCellProperties.indentChildren]) {
            buffer.withIndent(body)
        } else {
            body()
        }
    }

    fun <T> getProperty(key: CellPropertyKey<T>): T {
        return if (properties.isSet(key)) {
            properties.get(key)
        } else {
            parent.let { if (it != null) it.getProperty(key) else key.defaultValue }
        }
    }
}

class CellProperties : Freezable() {
    private val properties: MutableMap<CellPropertyKey<*>, Any?> = HashMap()
    operator fun <T> get(key: CellPropertyKey<T>): T {
        return if (properties.containsKey(key)) properties[key] as T else key.defaultValue
    }

    fun isSet(key: CellPropertyKey<*>): Boolean = properties.containsKey(key)

    operator fun <T> set(key: CellPropertyKey<T>, value: T) {
        checkNotFrozen()
        properties[key] = value
    }

    fun copy(): CellProperties {
        return CellProperties().also { it.addAll(this) }
    }

    fun addAll(from: CellProperties) {
        checkNotFrozen()
        properties += from.properties
    }
}

class CellPropertyKey<E>(val name: String, val defaultValue: E)

enum class ECellLayout {
    VERTICAL,
    HORIZONTAL;
}

object CommonCellProperties {
    val layout = CellPropertyKey<ECellLayout>("layout", ECellLayout.HORIZONTAL)
    val indentChildren = CellPropertyKey<Boolean>("indent-children", false)
    val onNewLine = CellPropertyKey<Boolean>("on-new-line", false)
    val noSpace = CellPropertyKey<Boolean>("no-space", false)
    val textColor = CellPropertyKey<String?>("text-color", null)
    val backgroundColor = CellPropertyKey<String?>("background-color", null)
}

interface ICellAction {

}

class TextCell(val text: String, val placeholderText: String): Cell() {
    override fun toString(): String = getVisibleText()

    fun getVisibleText(): String {
        return if (getChildren().isEmpty()) {
            text.ifEmpty { placeholderText }
        } else {
            """$text<${getChildren()}>"""
        }
    }

    override fun layout(buffer: LayoutedCells) {
        if (properties[CommonCellProperties.onNewLine]) buffer.onNewLine()
        if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
        buffer.append(LayoutableCell(this))
        if (properties[CommonCellProperties.noSpace]) buffer.noSpace()
    }
}
