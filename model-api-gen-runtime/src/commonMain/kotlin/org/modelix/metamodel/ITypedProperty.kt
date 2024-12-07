package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IProperty

interface ITypedProperty<ValueT : Any?> : ITypedConceptFeature {
    fun untyped(): IProperty
    fun serializeValue(value: ValueT): String?
    fun deserializeValue(serialized: String?): ValueT
}
fun <ValueT> INode.setTypedPropertyValue(property: ITypedProperty<ValueT>, value: ValueT) {
    setPropertyValue(property.untyped(), property.serializeValue(value))
}
fun <ValueT> INode.getTypedPropertyValue(property: ITypedProperty<ValueT>): ValueT {
    return property.deserializeValue(getPropertyValue(property.untyped()))
}
