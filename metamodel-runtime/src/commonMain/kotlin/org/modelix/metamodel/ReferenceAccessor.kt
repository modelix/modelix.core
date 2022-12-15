package org.modelix.metamodel

import org.modelix.model.api.INode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OptionalReferenceAccessor<SourceT, TargetT : ITypedNode>(
    val node: INode,
    val role: String,
    val targetType: KClass<TargetT>
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): TargetT? {
        return node.getReferenceTarget(role)?.typed(targetType)
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: TargetT?) {
        node.setReferenceTarget(role, target?.unwrap())
    }
}

class MandatoryReferenceAccessor<SourceT, TargetT : ITypedNode>(
    val node: INode,
    val role: String,
    val targetType: KClass<TargetT>
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): TargetT {
        return node.getReferenceTarget(role)?.typed(targetType) ?: throw RuntimeException("reference '$role' is not set")
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: TargetT) {
        node.setReferenceTarget(role, target.unwrap())
    }
}

class RawReferenceAccessor<SourceT>(
    val node: INode,
    val role: String
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): INode? {
        return node.getReferenceTarget(role)
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: INode?) {
        node.setReferenceTarget(role, target)
    }
}