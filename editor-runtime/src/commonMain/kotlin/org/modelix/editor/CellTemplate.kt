package org.modelix.editor

import org.modelix.metamodel.*
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.getChildren
import org.modelix.model.api.getReferenceTarget

abstract class CellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(val concept: GeneratedConcept<NodeT, ConceptT>) {
    val properties = CellProperties()
    val children: MutableList<CellTemplate<NodeT, ConceptT>> = ArrayList()
    val withNode: MutableList<(node: NodeT, Cell)->Unit> = ArrayList()
    open fun apply(editor: EditorEngine, node: NodeT): Cell {
        val cell = createCell(editor, node)
        cell.properties.addAll(properties)
        applyChildren(editor, node, cell)
        if (properties[CommonCellProperties.layout] == ECellLayout.VERTICAL) {
            cell.children.drop(1).forEach { it.properties[CommonCellProperties.onNewLine] = true }
        }
        withNode.forEach { it(node, cell) }
        return cell
    }
    protected open fun applyChildren(editor: EditorEngine, node: NodeT, cell: Cell) {
        cell.children += children.map { it.apply(editor, node) }
    }
    protected abstract fun createCell(editor: EditorEngine, node: NodeT): Cell
}

class ConstantCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val text: String)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = TextCell(text, "")
}
class NewLineCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell {
        return Cell().also { cell -> cell.properties[CommonCellProperties.onNewLine] = true }
    }
}
class NoSpaceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell {
        return Cell().also { cell -> cell.properties[CommonCellProperties.noSpace] = true }
    }
}
class CollectionCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = Cell()
}
class OptionalCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell {
        return Cell()
    }

    override fun applyChildren(editor: EditorEngine, node: NodeT, cell: Cell) {
        val childLinkCell = children.filterIsInstance<ChildCellTemplate<NodeT, *>>().firstOrNull()
        if (childLinkCell == null || !childLinkCell.getChildNodes(node).isEmpty()) {
            super.applyChildren(editor, node, cell)
        }
    }
}

open class PropertyCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val property: IProperty)
    : CellTemplate<NodeT, ConceptT>(concept) {
    var placeholderText: String = "<no ${property.name}>"
    override fun createCell(editor: EditorEngine, node: NodeT): Cell {
        val value = node.getPropertyValue(property)
        return TextCell(value ?: "", if (value == null) placeholderText else "")
    }
}

class ReferenceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept, TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept>(
    concept: GeneratedConcept<NodeT, ConceptT>,
    val link: GeneratedReferenceLink<TargetNodeT, TargetConceptT>,
    val presentation: TargetNodeT.() -> String?
)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell = TextCell(getText(node), "<no ${link.name}>")
    private fun getText(node: NodeT): String = getTargetNode(node)?.let(presentation) ?: ""
    private fun getTargetNode(sourceNode: NodeT): TargetNodeT? {
        return sourceNode._node.getReferenceTarget(link)?.typed() as TargetNodeT?
    }
}

class FlagCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, property: IProperty, val text: String)
    : PropertyCellTemplate<NodeT, ConceptT>(concept, property) {
    override fun createCell(editor: EditorEngine, node: NodeT) = if (node.getPropertyValue(property) == "true") TextCell(text, "") else Cell()
}

class ChildCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val link: IChildLink)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = Cell().also { cell ->
        val childNodes = getChildNodes(node)
        if (childNodes.isEmpty()) {
            cell.children += TextCell("", "<no ${link.name}>")
        } else {
            val childCells = childNodes.map { editor.createCell(it.typed()) }
            cell.children += childCells
        }
    }

    fun getChildNodes(node: NodeT) = node._node.getChildren(link).toList()
}
