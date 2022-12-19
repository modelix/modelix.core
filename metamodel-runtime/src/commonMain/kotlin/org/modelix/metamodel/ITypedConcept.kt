package org.modelix.metamodel

import org.modelix.model.api.IConcept
import kotlin.reflect.KClass

interface ITypedConcept {
    val _concept: IConcept
}

fun ITypedConcept.untyped(): IConcept = _concept

interface INonAbstractConcept<NodeT : ITypedNode> : ITypedConcept {
    fun getInstanceClass(): KClass<out NodeT>
}
