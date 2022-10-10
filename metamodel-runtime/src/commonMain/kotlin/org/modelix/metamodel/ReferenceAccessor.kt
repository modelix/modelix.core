package org.modelix.metamodel

import org.modelix.model.api.INode
import kotlin.js.JsExport
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.cast

@JsExport
class ReferenceAccessor<SourceT, TargetT : ITypedNode>(
    val node: INode,
    val role: String,
    val targetType: KClass<TargetT>
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): TargetT? {
        return node.getReferenceTarget(role)?.let { targetType.cast(it.typed()) }
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: TargetT?) {
        node.setReferenceTarget(role, target?._node)
    }
}