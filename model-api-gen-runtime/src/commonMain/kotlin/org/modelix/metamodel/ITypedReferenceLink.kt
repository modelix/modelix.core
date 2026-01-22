package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.async.IAsyncNode

interface ITypedReferenceLink<out TargetT : ITypedNode> : ITypedConceptFeature {
    fun untyped(): IReferenceLink
    fun castTarget(target: INode): TargetT
    fun getTypedTargetConcept(): IConceptOfTypedNode<TargetT>
}
fun <TargetT : ITypedNode> INode.getReferenceTargetOrNull(link: ITypedReferenceLink<TargetT>): TargetT? {
    return getReferenceTarget(link.untyped())?.let { link.castTarget(it) }
}
fun <TargetT : ITypedNode> INode.getReferenceTarget(link: ITypedReferenceLink<TargetT>): TargetT {
    val target = getReferenceTargetOrNull(link)
    if (target == null && !link.untyped().isOptional) throw ReferenceNotSetException(this, link)
    return target as TargetT
}
fun <TargetT : ITypedNode> INode.setReferenceTarget(link: ITypedReferenceLink<TargetT>, target: TargetT?) {
    setReferenceTarget(link.untyped(), target?.unwrap())
}

class ReferenceNotSetException(val node: INode, val link: ITypedReferenceLink<*>) :
    Exception("Node $node has no reference target in role ${link.untyped().name}")

fun IAsyncNode.getReferenceTarget(reference: ITypedReferenceLink<ITypedNode>) =
    getReferenceTarget(reference.untyped().toReference())
