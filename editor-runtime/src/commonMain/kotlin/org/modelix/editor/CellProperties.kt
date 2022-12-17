package org.modelix.editor

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

class CellPropertyKey<E>(val name: String, val defaultValue: E) {
    override fun toString() = name
}

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
