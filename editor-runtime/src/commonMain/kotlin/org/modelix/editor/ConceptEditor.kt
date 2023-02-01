package org.modelix.editor

import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedChildListLink
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.ITypedSingleChildLink
import org.modelix.metamodel.typed
import org.modelix.metamodel.untypedReference
import org.modelix.model.api.serialize

class ConceptEditor<NodeT : ITypedNode, ConceptT : ITypedConcept>(
    val declaredConcept: GeneratedConcept<NodeT, ConceptT>?,
    val templateBuilder: (subConcept: GeneratedConcept<NodeT, ConceptT>)->CellTemplate<NodeT, ConceptT>
) {
    fun apply(subConcept: GeneratedConcept<NodeT, ConceptT>): CellTemplate<NodeT, ConceptT> {
        return templateBuilder(subConcept)
            .also { it.setReference(RooCellTemplateReference(this, subConcept.getReference())) }
    }

    fun apply(context: CellCreationContext, node: NodeT): CellData {
        return apply(node._concept.untyped() as GeneratedConcept<NodeT, ConceptT>).apply(context, node)
    }
}

val defaultConceptEditor = ConceptEditor(null) { subConcept ->
    CellTemplateBuilder(CollectionCellTemplate(subConcept)).apply {
        subConcept.getShortName().constant()
        curlyBrackets {
            for (property in subConcept.getAllProperties()) {
                newLine()
                label(property.name + ":")
                property.cell()
            }
            for (link in subConcept.getAllReferenceLinks()) {
                newLine()
                label(link.name + ":")
                link.typed()?.cell(presentation = { untypedReference().serialize() })
            }
            for (link in subConcept.getAllChildLinks()) {
                newLine()
                label(link.name + ":")
                when (val l = link.typed()) {
                    is ITypedSingleChildLink -> l.cell()
                    is ITypedChildListLink -> {
                        newLine()
                        indented {
                            l.vertical()
                        }
                    }
                }
            }
        }
    }.template
}
