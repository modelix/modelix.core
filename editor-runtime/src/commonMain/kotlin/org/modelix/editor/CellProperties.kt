package org.modelix.editor

import kotlin.reflect.KProperty

class CellProperties : Freezable() {
    private val properties: MutableMap<CellPropertyKey<*>, Any?> = HashMap()
    operator fun <T> get(key: CellPropertyKey<T>): T {
        return if (properties.containsKey(key)) properties[key] as T else key.defaultValue
    }

    fun isSet(key: CellPropertyKey<*>): Boolean = properties.containsKey(key)

    operator fun <T> set(key: CellPropertyKey<T>, value: T) {
        checkNotFrozen()
//        if (isSet(key)) throw IllegalStateException("property '$key' is already set")
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

class CellPropertyKey<E>(val name: String, val defaultValue: E, val inherits: Boolean = false) {
    override fun toString() = name
}

fun <E> CellPropertyKey<E>.from(cell: Cell) = cell.data.properties[this]

enum class ECellLayout {
    VERTICAL,
    HORIZONTAL;
}

object CommonCellProperties {
    val layout = CellPropertyKey<ECellLayout>("layout", ECellLayout.HORIZONTAL)
    val indentChildren = CellPropertyKey<Boolean>("indent-children", false)
    val onNewLine = CellPropertyKey<Boolean>("on-new-line", false)
    val noSpace = CellPropertyKey<Boolean>("no-space", false)
    val textColor = CellPropertyKey<String?>("text-color", null, inherits = true)
    val placeholderTextColor = CellPropertyKey<String?>("placeholder-text-color", "lightGray", inherits = true)
    val backgroundColor = CellPropertyKey<String?>("background-color", null)
    val textReplacement = CellPropertyKey<String?>("text-replacement", null)
    val tabTarget = CellPropertyKey<Boolean>("tab-target", false) // caret is placed into the cell when navigating via TAB
    val selectable = CellPropertyKey<Boolean>("selectable", false)
}

fun Cell.isTabTarget() = getProperty(CommonCellProperties.tabTarget)
fun Cell.isSelectable() = getProperty(CommonCellProperties.selectable)
