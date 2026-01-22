package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.remove

interface ITypedChildLink<out ChildT : ITypedNode> : ITypedConceptFeature {
    fun untyped(): IChildLink
    fun castChild(childNode: INode): ChildT
    fun getTypedChildConcept(): IConceptOfTypedNode<ChildT>
}
interface ITypedSingleChildLink<ChildT : ITypedNode> : ITypedChildLink<ChildT>
interface ITypedChildListLink<ChildT : ITypedNode> : ITypedChildLink<ChildT>

interface ITypedMandatorySingleChildLink<ChildT : ITypedNode> : ITypedSingleChildLink<ChildT>

fun <ChildT : ITypedNode> INode.getChildren(link: ITypedChildLink<ChildT>): List<ChildT> {
    return getChildren(link.untyped()).map { link.castChild(it) }
}

fun <ChildT : ITypedNode> INode.getChild(link: ITypedSingleChildLink<ChildT>): ChildT? {
    return getChildren(link).firstOrNull()
}

fun <ChildT : ITypedNode> INode.getChild(link: ITypedMandatorySingleChildLink<ChildT>): ChildT {
    return getChildren(link).firstOrNull() ?: throw ChildNotSetException(this, link)
}

fun <ChildT : ITypedNode, ChildConceptT : INonAbstractConcept<ChildT>> INode.setNewChild(link: ITypedSingleChildLink<ChildT>, subConcept: ChildConceptT? = null): ChildT {
    getChildren(link).forEach { it.unwrap().remove() }
    return link.castChild(addNewChild(link.untyped(), 0, subConcept?.untyped()))
}

fun <ChildT : ITypedNode, ChildConceptT : INonAbstractConcept<ChildT>> INode.addNewChild(link: ITypedChildLink<ChildT>, index: Int = -1, subConcept: ChildConceptT? = null): ChildT {
    return link.castChild(addNewChild(link.untyped(), index, subConcept?.untyped()))
}

class ChildNotSetException(val node: INode, val link: ITypedMandatorySingleChildLink<*>) :
    Exception("Node $node has no child in role ${link.untyped().name}")

fun IAsyncNode.getChildren(link: ITypedChildLink<ITypedNode>) =
    getChildren(link.untyped().toReference())
