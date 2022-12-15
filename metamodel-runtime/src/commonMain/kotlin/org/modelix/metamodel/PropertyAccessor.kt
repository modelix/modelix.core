package org.modelix.metamodel

import org.modelix.model.api.INode
import kotlin.reflect.KProperty

abstract class PropertyAccessor<ValueT>(val node: INode, val role: String) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): ValueT {
        return convertRead(node.getPropertyValue(role))
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: ValueT) {
        node.setPropertyValue(role, convertWrite(value))
    }

    abstract fun convertRead(value: String?): ValueT
    abstract fun convertWrite(value: ValueT): String?
}

class RawPropertyAccessor(node: INode, role: String) : PropertyAccessor<String?>(node, role) {
    override fun convertRead(value: String?): String? = value
    override fun convertWrite(value: String?): String? = value
}

class StringPropertyAccessor(node: INode, role: String) : PropertyAccessor<String>(node, role) {
    override fun convertRead(value: String?): String = value ?: ""
    override fun convertWrite(value: String): String? = value
}

class BooleanPropertyAccessor(node: INode, role: String) : PropertyAccessor<Boolean>(node, role) {
    override fun convertRead(value: String?): Boolean = value == "true"
    override fun convertWrite(value: Boolean): String? = if (value) "true" else "false"
}

class IntPropertyAccessor(node: INode, role: String) : PropertyAccessor<Int>(node, role) {
    override fun convertRead(value: String?): Int = value?.toInt() ?: 0
    override fun convertWrite(value: Int): String? = value.toString()
}