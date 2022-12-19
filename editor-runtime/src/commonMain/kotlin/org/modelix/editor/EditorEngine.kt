package org.modelix.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.modelix.metamodel.ITypedNode
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.getAllConcepts
import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.incrementalFunction
import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.model.api.IConcept

class EditorEngine(incrementalEngine: IncrementalEngine? = null) {

    private val incrementalEngine: IncrementalEngine
    private val ownsIncrementalEngine: Boolean
    private val editors: MutableSet<LanguageEditors<*>> = HashSet()
    private val editorsForConcept: MutableMap<IConceptReference, MutableList<ConceptEditor<*, *>>> = LinkedHashMap()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        if (incrementalEngine == null) {
            this.incrementalEngine = IncrementalEngine(100_000)
            this.ownsIncrementalEngine = true
        } else {
            this.incrementalEngine = incrementalEngine
            this.ownsIncrementalEngine = false
        }
    }

    private val createCellIncremental: (ITypedNode)->Cell = this.incrementalEngine.incrementalFunction("createCell") { context, node ->
        val cell = doCreateCell(node)
        cell.freeze()
        LOG.trace { "Cell created for $node: $cell" }
        cell
    }
    private val createCellDataIncremental: (ITypedNode)->CellData = this.incrementalEngine.incrementalFunction("createCellData") { context, node ->
        val cellData = doCreateCellData(node)
        cellData.freeze()
        LOG.trace { "Cell created for $node: $cellData" }
        cellData
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

    fun createCellModel(concept: IConcept): CellTemplate<*, *> {
        val editor: ConceptEditor<ITypedNode, ITypedConcept> = resolveConceptEditor(concept) as ConceptEditor<ITypedNode, ITypedConcept>
        val template: CellTemplate<ITypedNode, ITypedConcept> = editor.apply(concept as GeneratedConcept<ITypedNode, ITypedConcept>)
        return template
    }

    fun editNode(node: ITypedNode): EditorComponent {
        return EditorComponent(this, { createCell(node) })
    }

    private fun doCreateCell(node: ITypedNode): Cell {
        return dataToCell(createCellDataIncremental(node))
    }

    private fun dataToCell(data: CellData): Cell {
        val cell = Cell(data)
        for (childData in data.children) {
            val childCell: Cell = when (childData) {
                is CellData -> {
                    dataToCell(childData)
                }
                is ChildNodeCellReference -> {
                    createCell(childData.childNode).also { it.parent?.removeChild(it) }
                }
                else -> throw RuntimeException("Unsupported: $childData")
            }
            cell.addChild(childCell)
        }
        return cell
    }

    private fun <NodeT : ITypedNode> doCreateCellData(node: NodeT): CellData {
        try {
            val editor = resolveConceptEditor(node._concept._concept) as ConceptEditor<NodeT, *>
            val data = editor.apply(this, node)
            data.properties[CellActionProperties.substitute] = ReplaceNodeActionProvider(LocationOfExistingNode(node.unwrap()))
            return data
        } catch (ex: Exception) {
            LOG.error(ex) { "Failed to create cell for $node" }
            return TextCellData("<ERROR: ${ex.message}>", "")
        }
    }

    private fun resolveConceptEditor(concept: IConcept): ConceptEditor<*, out ITypedConcept> {
        val editors = concept.getAllConcepts()
            .firstNotNullOfOrNull { editorsForConcept[it.getReference()] }
        return editors?.firstOrNull()
            ?: createDefaultConceptEditor(concept as GeneratedConcept<*, *>)
    }

    fun dispose() {
        coroutineScope.cancel("EditorEngine disposed")
        if (ownsIncrementalEngine) incrementalEngine.dispose()
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }
}


