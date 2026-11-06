package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class OptionalReferenceAccessor<SourceT, TargetT : ITypedNode>(
    val node: INode,
    val role: IReferenceLink,
    val targetType: KClass<TargetT>,
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): TargetT? {
        return node.getReferenceTarget(role)?.typed(targetType)
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: TargetT?) {
        node.setReferenceTarget(role, target?.unwrap())
    }

    /**
     * The reference target if it is set and its concept is provided by a registered generated
     * language, and `null` if the reference is unset or the target's concept is unknown.
     *
     * Additive, opt-in *lenient* view; reading the delegated property stays strict and reports an
     * unknown target as an [UnknownConceptException].
     */
    fun knownTargetOrNull(): TargetT? = node.getReferenceTarget(role)?.typedOrNull(targetType)
}

class MandatoryReferenceAccessor<SourceT, TargetT : ITypedNode>(
    val node: INode,
    val role: IReferenceLink,
    val targetType: KClass<TargetT>,
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): TargetT {
        return node.getReferenceTarget(role)?.typed(targetType) ?: throw RuntimeException("reference '$role' is not set")
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: TargetT) {
        node.setReferenceTarget(role, target.unwrap())
    }

    /**
     * The reference target if it is set and its concept is provided by a registered generated
     * language, and `null` if the reference is unset or the target's concept is unknown.
     *
     * Additive, opt-in *lenient* view; reading the delegated property stays strict and reports an
     * unknown target as an [UnknownConceptException].
     */
    fun knownTargetOrNull(): TargetT? = node.getReferenceTarget(role)?.typedOrNull(targetType)
}

class RawReferenceAccessor<SourceT>(
    val node: INode,
    val role: IReferenceLink,
) {
    operator fun getValue(thisRef: SourceT, property: KProperty<*>): INode? {
        return node.getReferenceTarget(role)
    }

    operator fun setValue(thisRef: SourceT, property: KProperty<*>, target: INode?) {
        node.setReferenceTarget(role, target)
    }
}
