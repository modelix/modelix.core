/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
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
