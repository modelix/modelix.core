package org.modelix.editor

import org.modelix.editor.Cell
import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.*


fun <LanguageT : ILanguage> languageEditors(language: LanguageT, body: LanguageEditors<LanguageT>.()->Unit): LanguageEditors<LanguageT> {
    return LanguageEditors(language).also(body)
}

class LanguageEditors<LanguageT : ILanguage>(val language: LanguageT) {
    val conceptEditors: MutableList<ConceptEditor<*, *>> = ArrayList()

    fun <NodeT : ITypedNode, ConceptT : GeneratedConcept<NodeT, TypedConceptT>, TypedConceptT : ITypedConcept> conceptEditor(concept: ConceptT, body: CellTemplateBuilder<NodeT, TypedConceptT>.()->Unit): ConceptEditor<NodeT, TypedConceptT> {
        return ConceptEditor(concept) { subConcept ->
            CellTemplateBuilder(CollectionCellTemplate(subConcept)).also(body).template
        }.also(conceptEditors::add)
    }

    fun register(editorEngine: EditorEngine) {
        editorEngine.registerEditors(this)
    }
}

interface ModelAccessBuilder {
    fun get(body: ()->String?)
    fun set(body: (String?)->Unit)
}