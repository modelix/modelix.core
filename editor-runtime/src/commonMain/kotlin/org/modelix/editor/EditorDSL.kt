package org.modelix.editor

import org.modelix.editor.Cell
import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.*


fun <LanguageT : ILanguage> languageEditors(language: LanguageT, body: LanguageEditors<LanguageT>.()->Unit): LanguageEditors<LanguageT> {
    TODO("Not implemented yet")
}

class LanguageEditors<LanguageT : ILanguage>(val language: LanguageT) {

    fun <NodeT : ITypedNode, ConceptT : GeneratedConcept<NodeT, TypedConceptT>, TypedConceptT : ITypedConcept> conceptEditor(concept: ConceptT, body: CellTemplateBuilder<Cell, NodeT, TypedConceptT>.()->Unit): CellTemplateBuilder<Cell, NodeT, TypedConceptT> {
        TODO("Not implemented yet")
    }

    fun register() {
        TODO("Not implemented yet")
    }
}

class ModelAccessBuilder {
    fun get(body: ()->String?) {

    }

    fun set(body: (String?)->Unit) {

    }
}