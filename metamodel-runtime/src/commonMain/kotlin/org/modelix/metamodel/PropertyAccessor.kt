package org.modelix.metamodel

import org.modelix.model.api.INode
import kotlin.js.JsExport
import kotlin.reflect.KProperty

@JsExport
class PropertyAccessor(val node: INode, val role: String) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): String? {
        return node.getPropertyValue(role)
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: String?) {
        node.setPropertyValue(role, value)
    }
}