package org.modelix.editor

import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode

class ConceptEditor<NodeT : ITypedNode, ConceptT : ITypedConcept>(
    val declaredConcept: GeneratedConcept<NodeT, ConceptT>,
    val templateBuilder: (subConcept: GeneratedConcept<NodeT, ConceptT>)->CellTemplate<NodeT, ConceptT>
) {
    fun apply(subConcept: GeneratedConcept<NodeT, ConceptT>): CellTemplate<NodeT, ConceptT> {
        return templateBuilder(subConcept)
    }

    fun apply(editor: EditorEngine, node: NodeT): Cell {
        return apply(node._concept._concept as GeneratedConcept<NodeT, ConceptT>).apply(editor, node)
    }
}