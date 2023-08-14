package org.modelix.metamodel

import org.modelix.model.api.IConcept
import kotlin.js.JsExport
import kotlin.reflect.KClass

@JsExport
interface ITypedConcept {
    @JsExport.Ignore
    fun untyped(): IConcept
}

@Deprecated("use .untyped()")
val ITypedConcept._concept: IConcept get() = untyped()

class FallbackTypedConcept(private val untypedConcept: IConcept) : ITypedConcept {
    override fun untyped(): IConcept = _concept
}

fun IConcept.typed(): ITypedConcept = when (this) {
    is GeneratedConcept<*, *> -> this.typed()
    else -> FallbackTypedConcept(this)
}

interface IConceptOfTypedNode<out NodeT : ITypedNode> : ITypedConcept {
    fun getInstanceInterface(): KClass<out NodeT>
}

@Deprecated("use .getInstanceInterface()")
fun <NodeT : ITypedNode> IConceptOfTypedNode<NodeT>.getInstanceClass(): KClass<out NodeT> {
    return getInstanceInterface()
}

@JsExport
interface INonAbstractConcept<out NodeT : ITypedNode> : IConceptOfTypedNode<NodeT>

interface ITypedConceptFeature
