package org.modelix.editor

import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.getAllConcepts
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.incrementalFunction

class EditorEngine(val incrementalEngine: IncrementalEngine = IncrementalEngine(100_000)) {

    private val editors: MutableSet<LanguageEditors<*>> = HashSet()
    private val editorsForConcept: MutableMap<IConceptReference, MutableList<ConceptEditor<*, *>>> = LinkedHashMap()
    private val createCellIncremental: (ITypedNode)->Cell = incrementalEngine.incrementalFunction("createCell") { context, node ->
        doCreateCell(node)
    }

    fun registerEditors(languageEditors: LanguageEditors<*>) {
        editors.add(languageEditors)
        languageEditors.conceptEditors.forEach {
            editorsForConcept.getOrPut(it.declaredConcept.getReference()) { ArrayList() }.add(it)
        }
    }

    fun <NodeT : ITypedNode> createCell(node: NodeT): Cell {
        return createCellIncremental(node)
    }

    private fun <NodeT : ITypedNode> doCreateCell(node: NodeT): Cell {
        val editors = node._concept._concept.getAllConcepts()
            .firstNotNullOfOrNull { editorsForConcept[it.getReference()] }
        val editor = editors?.firstOrNull() as ConceptEditor<NodeT, *>?
        if (editor != null) {
            return editor.apply(this, node)
        } else {
            return TextCell("<no editor for ${node._concept._concept.getLongName()}>", "")
        }
    }

    fun dispose() {
        incrementalEngine.dispose()
    }
}