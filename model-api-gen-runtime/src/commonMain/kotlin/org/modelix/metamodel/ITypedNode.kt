package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea
import kotlin.js.JsExport
import kotlin.reflect.KClass

@JsExport
interface ITypedNode {
    val _concept: ITypedConcept
    fun unwrap(): INode
}

fun ITypedNode.untyped() = unwrap()
fun ITypedNode.untypedConcept() = _concept.untyped()
fun ITypedNode.typedConcept() = _concept
fun ITypedNode.getPropertyValue(property: IProperty): String? = unwrap().getPropertyValue(property.name)
fun ITypedNode.instanceOf(concept: ITypedConcept): Boolean {
    return instanceOf(concept._concept)
}
fun ITypedNode.instanceOf(concept: IConcept): Boolean {
    return this._concept._concept.isSubConceptOf(concept)
}

inline fun <reified NodeT : ITypedNode> NodeT.typedReference() = TypedNodeReference(unwrap().reference, NodeT::class)
fun ITypedNode.untypedReference() = unwrap().reference
data class TypedNodeReference<NodeT : ITypedNode>(val ref: INodeReference, val nodeClass: KClass<NodeT>) {
    fun resolve(area: IArea?) = ref.resolveNode(area)?.typed(nodeClass)
    fun untyped() = ref
}
