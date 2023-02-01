package org.modelix.editor

import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.ITypedReferenceLink
import org.modelix.metamodel.getPropertyValue
import org.modelix.metamodel.getReferenceTargetOrNull
import org.modelix.metamodel.setReferenceTarget
import org.modelix.metamodel.typed
import org.modelix.metamodel.typedUnsafe
import org.modelix.metamodel.untyped
import org.modelix.metamodel.untypedReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getChildren
import org.modelix.model.api.moveChild
import org.modelix.model.api.setPropertyValue

abstract class CellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(val concept: GeneratedConcept<NodeT, ConceptT>) {
    val properties = CellProperties()
    private val children: MutableList<CellTemplate<NodeT, ConceptT>> = ArrayList()
    private var reference: ICellTemplateReference? = null
    val withNode: MutableList<(node: NodeT)->Unit> = ArrayList()
    fun apply(context: CellCreationContext, node: NodeT): CellData {
        val cellData = createCell(context, node)
        cellData.properties.addAll(properties)
        cellData.children.addAll(applyChildren(context, node, cellData))
        if (properties[CommonCellProperties.layout] == ECellLayout.VERTICAL) {
            cellData.children.drop(1).forEach { (it as CellData).properties[CommonCellProperties.onNewLine] = true }
        }
        withNode.forEach { it(node) }
        val cellReference: TemplateCellReference = createCellReference(node)
        cellData.cellReferences.add(cellReference)
        applyTextReplacement(cellData, context.editorState)
        return cellData
    }
    protected open fun applyChildren(context: CellCreationContext, node: NodeT, cell: CellData): List<CellData> {
        return children.map { it.apply(context, node) }
    }
    protected abstract fun createCell(context: CellCreationContext, node: NodeT): CellData

    open fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        return children.asSequence().mapNotNull { it.getInstantiationActions(location, parameters) }.firstOrNull()
    }

    fun getSideTransformActions(before: Boolean, nodeToReplace: INode): List<ICodeCompletionAction>? {
        val symbols = getGrammarSymbols().toList()
        val conceptToReplace = nodeToReplace.concept ?: return null
        return symbols.mapIndexedNotNull { index, symbol ->
            if (symbol is ChildCellTemplate<*, *> && conceptToReplace.isSubConceptOf(symbol.link.targetConcept)) {
                val prevNextIndex = if (before)index - 1 else index + 1
                val prevNextSymbol = symbols.getOrNull(prevNextIndex) ?: return@mapIndexedNotNull null
                return@mapIndexedNotNull prevNextSymbol.createWrapperAction(nodeToReplace, symbol.link)
            }
            return@mapIndexedNotNull null
        }.flatten()
    }

    fun getGrammarSymbols(): Sequence<IGrammarSymbol> {
        return if (this is IGrammarSymbol) {
            sequenceOf(this)
        } else {
            children.asSequence().flatMap { it.getGrammarSymbols() }
        }
    }

    fun addChild(child: CellTemplate<NodeT, ConceptT>) {
        children.add(child)
        reference?.let { child.setReference(ChildCellTemplateReference(it, children.size - 1)) }
    }

    fun getChildren(): List<CellTemplate<NodeT, ConceptT>> = children

    fun setReference(ref: ICellTemplateReference) {
        if (reference != null) throw IllegalStateException("reference is already set")
        reference = ref
        children.forEachIndexed { index, child -> child.setReference(ChildCellTemplateReference(ref, index)) }
    }

    fun getReference() = reference ?: throw IllegalStateException("reference isn't set yet")

    fun createCellReference(node: INode) = TemplateCellReference(getReference(), node.reference)
    fun createCellReference(node: ITypedNode) = createCellReference(node.untyped())

    private fun applyTextReplacement(cellData: CellData, editorState: EditorState) {
        if (cellData is TextCellData) {
            val cellRef = cellData.cellReferences.firstOrNull()
            if (cellRef != null) {
                editorState.textReplacements[cellRef]
                    ?.let { cellData.properties[CommonCellProperties.textReplacement] = it }
                cellData.properties[CellActionProperties.replaceText] =
                    OverrideText(cellData, cellData.properties[CellActionProperties.replaceText])
            }
        }
        cellData.children.filterIsInstance<CellData>().forEach { applyTextReplacement(it, editorState) }
    }
}

interface IGrammarSymbol {
    fun createWrapperAction(nodeToWrap: INode, wrappingLink: IChildLink): List<ICodeCompletionAction> {
        return emptyList()
    }
}

class OverrideText(val cell: TextCellData, val delegate: ITextChangeAction?) : ITextChangeAction {
    override fun isValid(value: String?): Boolean {
        return true
    }

    override fun replaceText(editor: EditorComponent, range: IntRange, replacement: String, newText: String): Boolean {
        val cellRef = cell.cellReferences.first()
        if (delegate != null && delegate.isValid(newText)) {
            editor.state.textReplacements.remove(cellRef)
            return delegate.replaceText(editor, range, replacement, newText)
        }

        if (cell.text == newText) {
            editor.state.textReplacements.remove(cellRef)
        } else {
            editor.state.textReplacements[cellRef] = newText
        }
        return true
    }
}

class ConstantCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val text: String)
    : CellTemplate<NodeT, ConceptT>(concept), IGrammarSymbol {
    override fun createCell(context: CellCreationContext, node: NodeT) = TextCellData(text, "")
    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        return listOf(InstantiateNodeAction(location))
    }

    override fun createWrapperAction(nodeToWrap: INode, wrappingLink: IChildLink): List<ICodeCompletionAction> {
        return listOf(SideTransformWrapper(nodeToWrap.toNonExisting(), wrappingLink))
    }

    inner class SideTransformWrapper(val nodeToWrap: INonExistingNode, val wrappingLink: IChildLink) : ICodeCompletionAction {
        override fun getMatchingText(): String = text
        override fun getDescription(): String = concept.getShortName()
        override fun execute(editor: EditorComponent) {
            val wrapper = nodeToWrap.getParent()!!.getOrCreateNode(null).addNewChild(nodeToWrap.getContainmentLink()!!, nodeToWrap.index(), concept)
            wrapper.moveChild(wrappingLink, 0, nodeToWrap.getOrCreateNode(null))
            editor.selectAfterUpdate {
                CaretPositionPolicy(wrapper)
                    .avoid(ChildNodeCellReference(wrapper.reference, wrappingLink))
                    .avoid(createCellReference(wrapper))
                    .getBestSelection(editor)
            }
        }

        override fun shadows(shadowed: ICodeCompletionAction): Boolean {
            if (shadowed !is ConstantCellTemplate<*, *>.SideTransformWrapper) return false
            if (shadowed.getTemplate().concept != concept) return false
            val commonAncestor = nodeToWrap.commonAncestor(shadowed.nodeToWrap)
            val ownDepth = nodeToWrap.ancestors(true).takeWhile { it != commonAncestor }.count()
            val otherDepth = shadowed.nodeToWrap.ancestors(true).takeWhile { it != commonAncestor }.count()
            if (ownDepth > otherDepth) return true
            return false
        }

        fun getTemplate() = this@ConstantCellTemplate
    }

    inner class InstantiateNodeAction(val location: INonExistingNode) : ICodeCompletionAction {
        override fun getMatchingText(): String {
            return text
        }

        override fun getDescription(): String {
            return concept.getShortName()
        }

        override fun execute(editor: EditorComponent) {
            val newNode = location.replaceNode(concept)
            editor.selectAfterUpdate {
                CaretPositionPolicy(newNode)
                    .getBestSelection(editor)
            }
        }
    }
}

/**
 * A label is some text shown in the editor that has no effect on its behavior.
 * It is not part of the grammar of the language.
 * It is ignored when generating transformation action.
 * A constant is part of the grammar.
 */
class LabelCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val text: String)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(context: CellCreationContext, node: NodeT): TextCellData {
        return TextCellData(text, "").also {
            if (!it.properties.isSet(CommonCellProperties.textColor)) {
                it.properties[CommonCellProperties.textColor] = "LightGray"
            }
        }
    }
    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        return emptyList()
    }
}

class NewLineCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(context: CellCreationContext, node: NodeT): CellData {
        return CellData().also { cell -> cell.properties[CommonCellProperties.onNewLine] = true }
    }
}
class NoSpaceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(context: CellCreationContext, node: NodeT): CellData {
        return CellData().also { cell -> cell.properties[CommonCellProperties.noSpace] = true }
    }
}
class CollectionCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(context: CellCreationContext, node: NodeT) = CellData()
}
class OptionalCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>)
    : CellTemplate<NodeT, ConceptT>(concept) {
    override fun createCell(context: CellCreationContext, node: NodeT): CellData {
        return CellData()
    }

    override fun applyChildren(context: CellCreationContext, node: NodeT, cell: CellData): List<CellData> {
        // TODO support other cell types as condition for the optional
        val childLinkCell = getChildren().filterIsInstance<ChildCellTemplate<NodeT, *>>().firstOrNull()
        if (childLinkCell == null || childLinkCell.getChildNodes(node).isNotEmpty()) {
            return super.applyChildren(context, node, cell)
        } else {
            return emptyList()
        }
    }

    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        return null // skip optional. Don't search in children.
    }
}

open class PropertyCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(concept: GeneratedConcept<NodeT, ConceptT>, val property: IProperty)
    : CellTemplate<NodeT, ConceptT>(concept), IGrammarSymbol {
    var placeholderText: String = "<no ${property.name}>"
    var validator: (String) -> Boolean = { true }
    override fun createCell(context: CellCreationContext, node: NodeT): CellData {
        val value = node.getPropertyValue(property)
        val data = TextCellData(value ?: "", if (value == null) placeholderText else "")
        data.properties[CellActionProperties.replaceText] = ChangePropertyAction(node)
        data.properties[CommonCellProperties.tabTarget] = true
        data.cellReferences += PropertyCellReference(property, node.untypedReference())
        return data
    }
    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        return listOf(WrapPropertyValueProvider(location))
    }

    inner class WrapPropertyValueProvider(val location: INonExistingNode) : ICodeCompletionActionProvider {
        override fun getApplicableActions(parameters: CodeCompletionParameters): List<IActionOrProvider> {
            return if (validator(parameters.pattern)) {
                listOf(WrapPropertyValue(location, parameters.pattern))
            } else {
                emptyList()
            }
        }
    }

    inner class WrapPropertyValue(val location: INonExistingNode, val value: String) : ICodeCompletionAction {
        override fun getMatchingText(): String {
            return value
        }

        override fun getDescription(): String {
            return concept.getShortName()
        }

        override fun execute(editor: EditorComponent) {
            val node = location.getOrCreateNode(concept)
            node.setPropertyValue(property, value)
            editor.selectAfterUpdate {
                CaretPositionPolicy(createCellReference(node))
                    .getBestSelection(editor)
            }
        }
    }

    inner class ChangePropertyAction(val node: ITypedNode) : ITextChangeAction {
        override fun isValid(value: String?): Boolean {
            if (value == null) return true
            return validator(value)
        }

        override fun replaceText(editor: EditorComponent, range: IntRange, replacement: String, newText: String): Boolean {
            node.unwrap().setPropertyValue(property, newText)
            return true
        }
    }
}

class ReferenceCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept, TargetNodeT : ITypedNode>(
    concept: GeneratedConcept<NodeT, ConceptT>,
    val link: ITypedReferenceLink<TargetNodeT>,
    val presentation: TargetNodeT.() -> String?
) : CellTemplate<NodeT, ConceptT>(concept), IGrammarSymbol {
    override fun createCell(context: CellCreationContext, node: NodeT): CellData {
        val data = TextCellData(getText(node), "<no ${link.untyped().name}>")
        data.cellReferences += ReferencedNodeCellReference(node.untypedReference(), link.untyped())
        data.properties[CommonCellProperties.tabTarget] = true
        return data
    }
    private fun getText(node: NodeT): String = getTargetNode(node)?.let(presentation) ?: ""
    private fun getTargetNode(sourceNode: NodeT): TargetNodeT? {
        return sourceNode.unwrap().getReferenceTargetOrNull(link)
    }
    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        val specializedLocation = location.ofSubConcept(concept)
        val targets = specializedLocation.getVisibleReferenceTargets(link.untyped())
        return targets.map { WrapReferenceTarget(location, it, presentation(it.typedUnsafe()) ?: "") }
    }

    inner class WrapReferenceTarget(val location: INonExistingNode, val target: INode, val presentation: String): ICodeCompletionAction {
        override fun getMatchingText(): String {
            return presentation
        }

        override fun getDescription(): String {
            return concept.getShortName()
        }

        override fun execute(editor: EditorComponent) {
            val sourceNode = location.getOrCreateNode(concept)
            sourceNode.setReferenceTarget(link, link.castTarget(target))
            editor.selectAfterUpdate {
                CaretPositionPolicy(createCellReference(sourceNode))
                    .getBestSelection(editor)
            }
        }
    }
}

class FlagCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(
    concept: GeneratedConcept<NodeT, ConceptT>,
    property: IProperty,
    val text: String
) : PropertyCellTemplate<NodeT, ConceptT>(concept, property), IGrammarSymbol {
    override fun createCell(context: CellCreationContext, node: NodeT) = if (node.getPropertyValue(property) == "true") TextCellData(text, "") else CellData()
    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        // TODO
        return listOf()
    }
}

class ChildCellTemplate<NodeT : ITypedNode, ConceptT : ITypedConcept>(
    concept: GeneratedConcept<NodeT, ConceptT>,
    val link: IChildLink
) : CellTemplate<NodeT, ConceptT>(concept), IGrammarSymbol {
    override fun createCell(context: CellCreationContext, node: NodeT) = CellData().also { cell ->
        val childNodes = getChildNodes(node)
        val substitutionPlaceholder = context.editorState.substitutionPlaceholderPositions[createCellReference(node)]
        val placeholderIndex = substitutionPlaceholder?.index?.coerceIn(0..childNodes.size) ?: 0
        val addSubstitutionPlaceholder: (Int) -> Unit = { index ->
            val isDefaultPlaceholder = childNodes.isEmpty()
            val placeholderText = if (isDefaultPlaceholder) "<no ${link.name}>" else "<choose ${link.name}>"
            val placeholder = TextCellData("", placeholderText)
            placeholder.properties[CellActionProperties.substitute] =
                ReplaceNodeActionProvider(NonExistingChild(node.untyped().toNonExisting(), link, index)).after {
                    context.editorState.substitutionPlaceholderPositions.remove(createCellReference(node))
                }
            placeholder.cellReferences.add(PlaceholderCellReference(createCellReference(node)))
            if (isDefaultPlaceholder) {
                placeholder.cellReferences += ChildNodeCellReference(node.untypedReference(), link, index)
            }
            placeholder.properties[CommonCellProperties.tabTarget] = true
            cell.addChild(placeholder)
        }
        val addInsertActionCell: (Int) -> Unit = { index ->
            if (link.isMultiple) {
                val actionCell = CellData()
                val action = InsertSubstitutionPlaceholderAction(context.editorState, createCellReference(node), index)
                actionCell.properties[CellActionProperties.insert] = action
                cell.addChild(actionCell)
            }
        }
        if (childNodes.isEmpty()) {
            addSubstitutionPlaceholder(0)
        } else {
            val childCells = childNodes.map { ChildDataReference(it.typed()) }
            childCells.forEachIndexed { index, child ->
                if (substitutionPlaceholder != null && placeholderIndex == index) {
                    addSubstitutionPlaceholder(placeholderIndex)
                } else {
                    addInsertActionCell(index)
                }

                //child.parent?.removeChild(child) // child may be cached and is still attached to the old parent
                val wrapper = CellData() // allow setting properties by the parent, because the cell is already frozen
                wrapper.addChild(child)
                wrapper.cellReferences += ChildNodeCellReference(node.untypedReference(), link, index)
                cell.addChild(wrapper)
            }
            if (substitutionPlaceholder != null && placeholderIndex == childNodes.size) {
                addSubstitutionPlaceholder(placeholderIndex)
            } else {
                addInsertActionCell(childNodes.size)
            }
        }
    }

    fun getChildNodes(node: NodeT) = node.unwrap().getChildren(link).toList()

    override fun getInstantiationActions(location: INonExistingNode, parameters: CodeCompletionParameters): List<IActionOrProvider>? {
        // TODO
        return listOf()
    }
}
data class PlaceholderCellReference(val childCellRef: TemplateCellReference) : CellReference()

class InsertSubstitutionPlaceholderAction(
    val editorState: EditorState,
    val ref: TemplateCellReference,
    val index: Int
) : ICellAction {
    override fun isApplicable(): Boolean = true

    override fun execute(editor: EditorComponent) {
        editorState.substitutionPlaceholderPositions[ref] = SubstitutionPlaceholderPosition(index)
        editor.selectAfterUpdate {
            editor.resolveCell(PlaceholderCellReference(ref))
                .firstOrNull()?.layoutable()?.let { CaretSelection(it, 0) }
        }
    }
}

fun CellTemplate<*, *>.firstLeaf(): CellTemplate<*, *> = if (getChildren().isEmpty()) this else getChildren().first().firstLeaf()
