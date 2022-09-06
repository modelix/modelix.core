package org.modelix.editor

import org.modelix.editor.Cell
import org.modelix.editor.TextCell
import org.modelix.metamodel.*
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty

abstract class CellTemplate<CellT : Cell, NodeT : ITypedNode, ConceptT : ITypedConcept>(val concept: GeneratedConcept<NodeT, ConceptT>) {
    val children: MutableList<CellTemplate<*, NodeT, ConceptT>> = ArrayList()
    val withNode: MutableList<(node: NodeT, CellT)->Unit> = ArrayList()
    fun apply(node: NodeT): CellT {
        val cell = createCell(node)
        cell.children += children.map { it.apply(node) }
        withNode.forEach { it(node, cell) }
        return cell
    }
    abstract fun createCell(node: NodeT): CellT
}

class PropertyCellTemplate<NodeT : TypedNodeImpl, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val property: IProperty)
    : CellTemplate<TextCell, NodeT, ConceptT>(concept) {
    override fun createCell(node: NodeT) = TextCell(node.getPropertyValue(property) ?: "", "<no ${property.name}>")
}

class ChildCellTemplate<NodeT : TypedNodeImpl, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val link: IChildLink)
    : CellTemplate<Cell, NodeT, ConceptT>(concept) {
    override fun createCell(node: NodeT) = Cell()
}
