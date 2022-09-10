package org.modelix.editor

import org.modelix.metamodel.*
import org.modelix.model.api.*

open class CellTemplateBuilder<NodeT : ITypedNode, ConceptT : ITypedConcept>(val template: CellTemplate<NodeT, ConceptT>) {
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

    fun String.cell(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        constant(this, body)
    }

    fun constant(text: String, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        CellTemplateBuilder(ConstantCellTemplate(template.concept, text))
            .also(body).template.also(template.children::add)
    }

    fun textColor(color: String) {
        TODO("Not implemented yet")
    }

    fun backgroundColor(color: String) {
        TODO("Not implemented yet")
    }

    fun vertical(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        // TODO add correct layout information
        CellTemplateBuilder(CollectionCellTemplate(template.concept))
            .also { it.template.properties[CommonCellProperties.layout] = ECellLayout.VERTICAL }.also(body).template.also(template.children::add)
    }

    fun horizontal(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        // TODO add layout information
        CellTemplateBuilder(CollectionCellTemplate(template.concept))
            .also(body).template.also(template.children::add)
    }

    fun optional(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        CellTemplateBuilder(OptionalCellTemplate<NodeT, ConceptT>(template.concept))
            .also(body).template.also(template.children::add)
    }

    fun brackets(singleLine: Boolean = true, leftSymbol: String, rightSymbol: String, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
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

    fun parentheses(singleLine: Boolean = true, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "(", ")", body)
    }

    fun curlyBrackets(singleLine: Boolean = false, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "{", "}", body)
    }

    fun angleBrackets(singleLine: Boolean = true, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "<", ">", body)
    }

    fun squareBrackets(singleLine: Boolean = true, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        brackets(singleLine, "[", "]", body)
    }

    /**
     * The next cell appears on a new line.
     * Multiple consecutive newLine's are merged to a single one. See also emptyLine()
     */
    fun newLine() {
        CellTemplateBuilder(NewLineCellTemplate(template.concept))
            .template.also(template.children::add)
    }

    /**
     * The next cell appears two lines below the current line.
     */
    fun emptyLine() {
        newLine()
        constant("")
        newLine()
    }

    fun noSpace() {
        CellTemplateBuilder(NoSpaceCellTemplate(template.concept))
            .template.also(template.children::add)
    }

    fun indented(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        horizontal {
            template.properties[CommonCellProperties.indentChildren] = true
            body()
        }
    }

    /**
     * The content is foldable
     */
    fun foldable(foldedText: String = "...", body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        // TODO
        horizontal(body)
    }

    /**
     * The current cell is foldable
     */
    fun foldable(foldedText: String = "...") {
        // TODO
    }

    fun IProperty.cell(body: PropertyCellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        propertyCell(body)
    }

    fun IProperty.propertyCell(body: PropertyCellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        PropertyCellTemplateBuilder(PropertyCellTemplate(template.concept, this))
            .also(body).template.also(template.children::add)
    }

    fun IProperty.flagCell(text: String? = null, body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        PropertyCellTemplateBuilder(FlagCellTemplate(template.concept, this, text ?: name))
            .also(body).template.also(template.children::add)
    }

    fun <TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept> GeneratedReferenceLink<TargetNodeT, TargetConceptT>.cell(presentation: TargetNodeT.()->String?, body: ReferenceCellTemplateBuilder<NodeT, ConceptT, TargetNodeT, TargetConceptT>.()->Unit = {}) {
        ReferenceCellTemplateBuilder(ReferenceCellTemplate(template.concept, this, presentation), this)
            .also(body).template.also(template.children::add)
    }

    fun GeneratedSingleChildLink<*, *>.cell(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        CellTemplateBuilder(ChildCellTemplate(template.concept, this))
            .also(body).template.also(template.children::add)
    }

    fun GeneratedChildListLink<*, *>.vertical(body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        // TODO add layout information
        horizontal {
            template.properties[CommonCellProperties.layout] = ECellLayout.VERTICAL
            body()
        }
    }

    fun GeneratedChildListLink<*, *>.horizontal(separator: String? = ",", body: CellTemplateBuilder<NodeT, ConceptT>.()->Unit = {}) {
        CellTemplateBuilder(ChildCellTemplate(template.concept, this))
            .also(body).template.also(template.children::add)
    }

    fun modelAccess(body: ModelAccessBuilder.()->Unit) {
        var setter: (String?)->Unit = {}
        var getter: ()->String? = { "<getter missing>" }
        body(object : ModelAccessBuilder {
            override fun get(body: () -> String?) {
                getter = body
            }

            override fun set(body: (String?) -> Unit) {
                setter = body
            }
        })
        modelAccess(getter, setter)
    }

    fun modelAccess(getter: ()->String?, setter: (String?)->Unit) {
        // TODO ModelAccessCellTemplate
        CellTemplateBuilder(ConstantCellTemplate(template.concept, "<model access>"))
            .template.also(template.children::add)
    }

    inner class WithNodeContext(val node: NodeT)
}

class PropertyCellTemplateBuilder<NodeT : ITypedNode, ConceptT : ITypedConcept>(template: PropertyCellTemplate<NodeT, ConceptT>) : CellTemplateBuilder<NodeT, ConceptT>(
    template
) {
    fun validateValue(validator: (String)->Boolean) {
        // TODO
    }

    fun readReplace(replacement: (String)->String) {
        // TODO
    }

    fun writeReplace(replacement: (String)->String) {
        // TODO
    }

    fun placeholderText(placeholderText: String) {
        (template as PropertyCellTemplate).placeholderText = placeholderText
    }
}

class ReferenceCellTemplateBuilder<SourceNodeT : ITypedNode, SourceConceptT : ITypedConcept, TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept>(template: CellTemplate<SourceNodeT, SourceConceptT>, val link: GeneratedReferenceLink<TargetNodeT, TargetConceptT>) : CellTemplateBuilder<SourceNodeT, SourceConceptT>(
    template
) {
    fun presentation(f: (TargetNodeT)->String?) {
        TODO("Not implemented yet")
    }

    fun withTargetNode(body: WithTargetNodeContext.()->Unit) {
        withNode {
            val targetNode: ITypedNode? = node._node.getReferenceTarget(link)?.let { it.typed() }
            if (targetNode != null) {
                body(WithTargetNodeContext(node, targetNode as TargetNodeT))
            }
        }
    }

    inner class WithTargetNodeContext(val sourceNode: SourceNodeT, val targetNode: TargetNodeT)
}