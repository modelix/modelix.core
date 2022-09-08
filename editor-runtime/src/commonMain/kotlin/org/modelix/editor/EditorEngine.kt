package org.modelix.editor

import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.getAllConcepts

class EditorEngine {

    private val editors: MutableSet<LanguageEditors<*>> = HashSet()
    private val editorsForConcept: MutableMap<IConceptReference, MutableList<ConceptEditor<*, *>>> = LinkedHashMap()

    fun registerEditors(languageEditors: LanguageEditors<*>) {
        editors.add(languageEditors)
        languageEditors.conceptEditors.forEach {
            editorsForConcept.getOrPut(it.declaredConcept.getReference()) { ArrayList() }.add(it)
        }
    }

    fun <NodeT : ITypedNode> createCell(node: NodeT): Cell {
        val editors = node._concept._concept.getAllConcepts()
            .firstNotNullOfOrNull { editorsForConcept[it.getReference()] }
        val editor = editors?.firstOrNull() as ConceptEditor<NodeT, *>?
        if (editor != null) {
            return editor.apply(this, node)
        } else {
            return TextCell("<no editor for ${node._concept._concept.getLongName()}>", "")
        }
    }

}