package org.modelix.editor

import org.modelix.metamodel.*
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.getChildren
import org.modelix.model.api.getReferenceTarget
import org.modelix.model.api.setPropertyValue

abstract class CellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(val concept: GeneratedConcept<NodeT, ConceptT>) {
    val properties = CellProperties()
    val children: MutableList<CellTemplate<NodeT, ConceptT>> = ArrayList()
    val withNode: MutableList<(node: NodeT)->Unit> = ArrayList()
    open fun apply(editor: EditorEngine, node: NodeT): CellData {
        val cellData = createCell(editor, node)
        cellData.properties.addAll(properties)
        cellData.children.addAll(applyChildren(editor, node, cellData))
        if (properties[CommonCellProperties.layout] == ECellLayout.VERTICAL) {
            cellData.children.drop(1).forEach { (it as CellData).properties[CommonCellProperties.onNewLine] = true }
        }
        withNode.forEach { it(node) }
        return cellData
    }
    protected open fun applyChildren(editor: EditorEngine, node: NodeT, cell: CellData): List<CellData> {
        return children.map { it.apply(editor, node) }
    }
    protected abstract fun createCell(editor: EditorEngine, node: NodeT): CellData
}

class ConstantCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val text: String)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = TextCellData(text, "")
}
class NewLineCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): CellData {
        return CellData().also { cell -> cell.properties[CommonCellProperties.onNewLine] = true }
    }
}
class NoSpaceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): CellData {
        return CellData().also { cell -> cell.properties[CommonCellProperties.noSpace] = true }
    }
}
class CollectionCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = CellData()
}
class OptionalCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): CellData {
        return CellData()
    }

    override fun applyChildren(editor: EditorEngine, node: NodeT, cell: CellData): List<CellData> {
        val childLinkCell = children.filterIsInstance<ChildCellTemplate<NodeT, *>>().firstOrNull()
        if (childLinkCell == null || childLinkCell.getChildNodes(node).isNotEmpty()) {
            return super.applyChildren(editor, node, cell)
        } else {
            return emptyList()
        }
    }
}

open class PropertyCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val property: IProperty)
    : CellTemplate<NodeT, ConceptT>(concept) {
    var placeholderText: String = "<no ${property.name}>"
    override fun createCell(editor: EditorEngine, node: NodeT): CellData {
        val value = node.getPropertyValue(property)
        val data = TextCellData(value ?: "", if (value == null) placeholderText else "")
        data.actions += ChangePropertyAction(node, property)
        data.cellReferences += PropertyCellReference(property, node.untypedReference())
        return data
    }
}

class ChangePropertyAction(val node: ITypedNode, val property: IProperty) : ITextChangeAction {
    override fun replaceText(range: IntRange, replacement: String): Boolean {
        val text = node.getPropertyValue(property) ?: ""
        val newText = text.replaceRange(range, replacement)
        node.unwrap().setPropertyValue(property, newText)
        return true
    }
}

class ReferenceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept, TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept>(
    concept: GeneratedConcept<NodeT, ConceptT>,
    val link: GeneratedReferenceLink<TargetNodeT, TargetConceptT>,
    val presentation: TargetNodeT.() -> String?
)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT): CellData = TextCellData(getText(node), "<no ${link.name}>")
    private fun getText(node: NodeT): String = getTargetNode(node)?.let(presentation) ?: ""
    private fun getTargetNode(sourceNode: NodeT): TargetNodeT? {
        return sourceNode.unwrap().getReferenceTarget(link)?.typedUnsafe()
    }
}

class FlagCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, property: IProperty, val text: String)
    : PropertyCellTemplate<NodeT, ConceptT>(concept, property) {
    override fun createCell(editor: EditorEngine, node: NodeT) = if (node.getPropertyValue(property) == "true") TextCellData(text, "") else CellData()
}

class ChildCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val link: IChildLink)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(editor: EditorEngine, node: NodeT) = CellData().also { cell ->
        val childNodes = getChildNodes(node)
        if (childNodes.isEmpty()) {
            cell.addChild(TextCellData("", "<no ${link.name}>"))
        } else {
            val childCells = childNodes.map { ChildNodeCellReference(it.typed()) }
            childCells.forEach {child ->
                //child.parent?.removeChild(child) // child may be cached and is still attached to the old parent
                val wrapper = CellData() // allow setting properties by the parent, because the cell is already frozen
                wrapper.addChild(child)
                cell.addChild(wrapper)
            }
        }
    }

    fun getChildNodes(node: NodeT) = node.unwrap().getChildren(link).toList()
}
