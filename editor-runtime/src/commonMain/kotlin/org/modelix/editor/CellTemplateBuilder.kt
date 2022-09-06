package org.modelix.editor

import org.modelix.editor.Cell
import org.modelix.editor.CellProperties
import org.modelix.editor.TextCell
import org.modelix.metamodel.GeneratedReferenceLink
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.LanguageRegistry
import org.modelix.model.api.*

open class CellTemplateBuilder<CellT : Cell, NodeT : ITypedNode, ConceptT : ITypedConcept>(val template: CellTemplate<CellT, NodeT, ConceptT>) {
    val concept: ConceptT = template.concept._typed
    val properties = CellProperties()

    fun ifEmpty(link: IChildLink, body: ()->Unit) {
        withNode {
            if (!node._node.getChildren(link).iterator().hasNext()) {
                body()
            }
        }
    }

    fun ifNotEmpty(link: IChildLink, body: ()->Unit) {
        withNode {
            if (node._node.getChildren(link).iterator().hasNext()) {
                body()
            }
        }
    }

    fun withNode(body: WithNodeContext.()->Unit) {
        template.withNode += { node, _ -> body(WithNodeContext(node)) }
    }

    fun String.cell(body: CellTemplateBuilder<TextCell, NodeT, ConceptT>.()->Unit = {}) {
        constant(this, body)
    }

    fun constant(text: String, body: CellTemplateBuilder<TextCell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun textColor(color: String) {
        TODO("Not implemented yet")
    }

    fun backgroundColor(color: String) {
        TODO("Not implemented yet")
    }

    fun vertical(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun horizontal(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun optional(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun brackets(singleLine: Boolean, leftSymbol: String, rightSymbol: String, body: CellTemplateBuilder<CellT, NodeT, ConceptT>.()->Unit = {}) {
        if (singleLine) {
            constant(leftSymbol)
            noSpace()
            body()
            noSpace()
            constant(rightSymbol)
        } else {
            constant(leftSymbol)
            foldable {
                newLine()
                indented {
                    body()
                }
                newLine()
            }
            constant(rightSymbol)
        }
    }

    fun parentheses(singleLine: Boolean = false, body: CellTemplateBuilder<CellT, NodeT, ConceptT>.()->Unit = {}) {
        brackets(true, "(", ")", body)
    }

    fun curlyBrackets(singleLine: Boolean = false, body: CellTemplateBuilder<CellT, NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "{", "}", body)
    }

    fun angleBrackets(singleLine: Boolean = false, body: CellTemplateBuilder<CellT, NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "<", ">", body)
    }

    fun squareBrackets(singleLine: Boolean = false, body: CellTemplateBuilder<CellT, NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "[", "]", body)
    }

    /**
     * The next cell appears on a new line.
     * Multiple consecutive newLine's are merged to a single one. See also emptyLine()
     */
    fun newLine() {
        TODO("Not implemented yet")
    }

    /**
     * The next cell appears two lines below the current line.
     */
    fun emptyLine() {
        TODO("Not implemented yet")
    }

    fun noSpace() {
        TODO("Not implemented yet")
    }

    fun indented(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    /**
     * The content is foldable
     */
    fun foldable(foldedText: String = "...", body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    /**
     * The current cell is foldable
     */
    fun foldable(foldedText: String = "...") {
        TODO("Not implemented yet")
    }

    fun property(property: IProperty): Cell {
        TODO("Not implemented yet")
    }

    fun property(getter: ConceptT.()-> IProperty) {
        TODO("Not implemented yet")
    }

    fun IProperty.cell(body: PropertyCellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        propertyCell(body)
    }

    fun IProperty.propertyCell(body: PropertyCellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun IProperty.flagCell(text: String? = null, body: CellTemplateBuilder<TextCell, NodeT, ConceptT>.()->Unit = {}) {
        TODO("Not implemented yet")
    }

    fun flags(vararg properties: IProperty) {

    }

    fun <TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept> GeneratedReferenceLink<TargetNodeT, TargetConceptT>.cell(presentation: TargetNodeT.()->String?, body: ReferenceCellTemplateBuilder<NodeT, ConceptT, TargetNodeT, TargetConceptT>.()->Unit = {}): Cell {
        TODO("Not implemented yet")
    }

    fun IChildLink.cell(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}): Cell {
        TODO("Not implemented yet")
    }

    fun IChildLink.vertical(body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}): Cell {
        TODO("Not implemented yet")
    }

    fun IChildLink.horizontal(separator: String = ",", body: CellTemplateBuilder<Cell, NodeT, ConceptT>.()->Unit = {}): Cell {
        TODO("Not implemented yet")
    }

    fun reference(link: IReferenceLink) {
        TODO("Not implemented yet")
    }

    fun modelAccess(body: ModelAccessBuilder.()->Unit) {
        TODO("Not implemented yet")
    }

    fun modelAccess(getter: ()->String?, setter: (String?)->Unit) {
        TODO("Not implemented yet")
    }

    fun build(): CellT {
        TODO("Not implemented yet")
    }

    inner class WithNodeContext(val node: NodeT)
}

class PropertyCellTemplateBuilder<NodeT : ITypedNode, ConceptT : ITypedConcept>(template: CellTemplate<TextCell, NodeT, ConceptT>) : CellTemplateBuilder<TextCell, NodeT, ConceptT>(
    template
) {
    fun validateValue(validator: (String)->Boolean) {
        TODO("Not implemented yet")
    }

    fun readReplace(replacement: (String)->String) {
        TODO("Not implemented yet")
    }

    fun writeReplace(replacement: (String)->String) {
        TODO("Not implemented yet")
    }
}

class ReferenceCellTemplateBuilder<SourceNodeT : ITypedNode, SourceConceptT : ITypedConcept, TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept>(template: CellTemplate<TextCell, SourceNodeT, SourceConceptT>, val link: GeneratedReferenceLink<TargetNodeT, TargetConceptT>) : CellTemplateBuilder<TextCell, SourceNodeT, SourceConceptT>(
    template
) {
    fun presentation(f: (TargetNodeT)->String?) {
        TODO("Not implemented yet")
    }

    fun withTargetNode(body: WithTargetNodeContext.()->Unit) {
        withNode {
            val targetNode: ITypedNode? = node._node.getReferenceTarget(link)?.let { LanguageRegistry.wrapNode(it) }
            if (targetNode != null) {
                body(WithTargetNodeContext(node, targetNode as TargetNodeT))
            }
        }
    }

    inner class WithTargetNodeContext(val sourceNode: SourceNodeT, val targetNode: TargetNodeT)
}