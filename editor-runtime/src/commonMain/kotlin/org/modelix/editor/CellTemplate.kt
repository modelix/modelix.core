package org.modelix.editor

import org.modelix.metamodel.*
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.getChildren
import org.modelix.model.api.getReferenceTarget

abstract class CellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(val concept: GeneratedConcept<NodeT, ConceptT>) {
    val children: MutableList<CellTemplate<NodeT, ConceptT>> = ArrayList()
    val withNode: MutableList<(node: NodeT, Cell)->Unit> = ArrayList()
    fun apply(editor: EditorEngine, node: NodeT): Cell {
        val cell = createCell(editor, node)
        cell.children += children.map { it.apply(editor, node) }
        withNode.forEach { it(node, cell) }
        return cell
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
        return Cell().also { cell -> cell.textLayoutHandlers += { it.onNewLine() } }
    }
}
class NoSpaceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell {
        return Cell().also { cell -> cell.textLayoutHandlers += { it.noSpace() } }
    }
}
class CollectionCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = Cell()
}
class OptionalCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = Cell()
}

open class PropertyCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val property: IProperty)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): Cell = TextCell(node.getPropertyValue(property) ?: "", "<no ${property.name}>")
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
        val childNodes = node._node.getChildren(link).toList()
        if (childNodes.isEmpty()) {
            cell.children += TextCell("", "<no ${link.name}>")
        } else {
            cell.children += childNodes.map { editor.createCell(it.typed()) }
        }
    }
}
