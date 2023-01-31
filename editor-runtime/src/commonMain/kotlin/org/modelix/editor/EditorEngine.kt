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
import org.modelix.metamodel.untyped
import org.modelix.metamodel.untypedReference
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

    private val createCellIncremental: (EditorState, ITypedNode)->Cell = this.incrementalEngine.incrementalFunction("createCell") { _, editorState, node ->
        val cell = doCreateCell(editorState, node)
        cell.freeze()
        LOG.trace { "Cell created for $node: $cell" }
        cell
    }
    private val createCellDataIncremental: (EditorState, ITypedNode)->CellData = this.incrementalEngine.incrementalFunction("createCellData") { _, editorState, node ->
        val cellData = doCreateCellData(editorState, node)
        cellData.freeze()
        LOG.trace { "Cell created for $node: $cellData" }
        cellData
    }

    fun registerEditors(languageEditors: LanguageEditors<*>) {
        editors.add(languageEditors)
        languageEditors.conceptEditors.forEach {
            val declaredConcept = it.declaredConcept ?: return@forEach
            editorsForConcept.getOrPut(declaredConcept.getReference()) { ArrayList() }.add(it)
        }
    }

    fun <NodeT : ITypedNode> createCell(editorState: EditorState, node: NodeT): Cell {
        return createCellIncremental(editorState, node)
    }

    fun createCellModel(concept: IConcept): CellTemplate<*, *> {
        val editor: ConceptEditor<ITypedNode, ITypedConcept> = resolveConceptEditor(concept) as ConceptEditor<ITypedNode, ITypedConcept>
        val template: CellTemplate<ITypedNode, ITypedConcept> = editor.apply(concept as GeneratedConcept<ITypedNode, ITypedConcept>)
        return template
    }

    fun editNode(node: ITypedNode): EditorComponent {
        return EditorComponent(this) { editorState ->
            node.unwrap().getArea().executeRead { createCell(editorState, node) }
        }
    }

    private fun doCreateCell(editorState: EditorState, node: ITypedNode): Cell {
        return dataToCell(editorState, createCellDataIncremental(editorState, node))
    }

    private fun dataToCell(editorState: EditorState, data: CellData): Cell {
        val cell = Cell(data)
        for (childData in data.children) {
            val childCell: Cell = when (childData) {
                is CellData -> {
                    dataToCell(editorState, childData)
                }
                is ChildDataReference -> {
                    createCell(editorState, childData.childNode).also { it.parent?.removeChild(it) }
                }
                else -> throw RuntimeException("Unsupported: $childData")
            }
            cell.addChild(childCell)
        }
        return cell
    }

    private fun <NodeT : ITypedNode> doCreateCellData(editorState: EditorState, node: NodeT): CellData {
        try {
            val editor = resolveConceptEditor(node._concept.untyped()) as ConceptEditor<NodeT, *>
            val data = editor.apply(CellCreationContext(this, editorState), node)
            data.properties[CellActionProperties.substitute] = ReplaceNodeActionProvider(ExistingNode(node.unwrap()))
            data.cellReferences += NodeCellReference(node.untypedReference())
            data.properties[CellActionProperties.transformBefore] = SideTransformNode(true, node.untyped())
            data.properties[CellActionProperties.transformAfter] = SideTransformNode(false, node.untyped())
            data.properties[CommonCellProperties.selectable] = true
            return data
        } catch (ex: Exception) {
            LOG.error(ex) { "Failed to create cell for $node" }
            return TextCellData("<ERROR: ${ex.message}>", "").apply {
                properties[CommonCellProperties.textColor] = "red"
            }
        }
    }

    private fun resolveConceptEditor(concept: IConcept): ConceptEditor<*, out ITypedConcept> {
        val editors = concept.getAllConcepts()
            .firstNotNullOfOrNull { editorsForConcept[it.getReference()] }
        return editors?.firstOrNull()
            ?: defaultConceptEditor
    }

    fun dispose() {
        coroutineScope.cancel("EditorEngine disposed")
        if (ownsIncrementalEngine) incrementalEngine.dispose()
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }
}


