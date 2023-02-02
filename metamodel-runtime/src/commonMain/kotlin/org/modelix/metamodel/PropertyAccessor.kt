package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.getPropertyValue
import org.modelix.model.api.setPropertyValue
import kotlin.reflect.KProperty

abstract class PropertyAccessor<ValueT>(val node: INode, val role: IProperty) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): ValueT {
        return convertRead(node.getPropertyValue(role))
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: ValueT) {
        node.setPropertyValue(role, convertWrite(value))
    }

    abstract fun convertRead(value: String?): ValueT
    abstract fun convertWrite(value: ValueT): String?
}

class TypedPropertyAccessor<ValueT>(val node: INode, val role: ITypedProperty<ValueT>) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): ValueT {
        return node.getTypedPropertyValue(role)
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: ValueT) {
        node.setTypedPropertyValue(role, value)
    }
}

class RawPropertyAccessor(node: INode, role: IProperty) : PropertyAccessor<String?>(node, role) {
    override fun convertRead(value: String?): String? = value
    override fun convertWrite(value: String?): String? = value
}

class StringPropertyAccessor(node: INode, role: IProperty) : PropertyAccessor<String>(node, role) {
    override fun convertRead(value: String?): String = value ?: ""
    override fun convertWrite(value: String): String? = value
}

class BooleanPropertyAccessor(node: INode, role: IProperty) : PropertyAccessor<Boolean>(node, role) {
    override fun convertRead(value: String?): Boolean = value == "true"
    override fun convertWrite(value: Boolean): String? = if (value) "true" else "false"
}

class IntPropertyAccessor(node: INode, role: IProperty) : PropertyAccessor<Int>(node, role) {
    override fun convertRead(value: String?): Int = value?.toInt() ?: 0
    override fun convertWrite(value: Int): String? = value.toString()
}