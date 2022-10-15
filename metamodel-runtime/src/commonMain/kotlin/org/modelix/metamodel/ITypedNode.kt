package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import kotlin.js.JsExport

@JsExport
interface ITypedNode {
    val _concept: ITypedConcept
    fun unwrap(): INode
}

fun ITypedNode.getPropertyValue(property: IProperty): String? = unwrap().getPropertyValue(property.name)
fun ITypedNode.instanceOf(concept: ITypedConcept): Boolean {
    return instanceOf(concept._concept)
}
fun ITypedNode.instanceOf(concept: IConcept): Boolean {
    return this._concept._concept.isSubConceptOf(concept)
}