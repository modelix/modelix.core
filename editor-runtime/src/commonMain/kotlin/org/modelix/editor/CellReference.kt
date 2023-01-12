package org.modelix.editor

import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.untyped
import org.modelix.metamodel.untypedReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

/**
 * A cell can have multiple CellReferences. Multiple CellReferences can resolve to the same cell.
 */
abstract class CellReference {

}

data class PropertyCellReference(val property: IProperty, val nodeRef: INodeReference) : CellReference()

fun EditorComponent.resolvePropertyCell(property: IProperty, nodeRef: INodeReference): Cell? =
    resolveCell(PropertyCellReference(property, nodeRef)).firstOrNull()

fun EditorComponent.resolvePropertyCell(property: IProperty, node: INode): Cell? =
    resolvePropertyCell(property, node.reference)

fun EditorComponent.resolvePropertyCell(property: IProperty, node: ITypedNode): Cell? =
    resolvePropertyCell(property, node.untyped())

data class NodeCellReference(val nodeRef: INodeReference) : CellReference()

fun EditorComponent.resolveNodeCell(nodeRef: INodeReference): Cell? =
    resolveCell(NodeCellReference(nodeRef)).firstOrNull()

fun EditorComponent.resolveNodeCell(node: INode): Cell? =
    resolveNodeCell(node.reference)

fun EditorComponent.resolveNodeCell(node: ITypedNode): Cell? =
    resolveNodeCell(node.untypedReference())

data class ChildNodeCellReference(val parentNodeRef: INodeReference, val link: IChildLink, val index: Int = 0) : CellReference()
data class ReferencedNodeCellReference(val sourceNodeRef: INodeReference, val link: IReferenceLink) : CellReference()
