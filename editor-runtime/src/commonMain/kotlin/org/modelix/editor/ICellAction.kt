package org.modelix.editor

import org.modelix.model.api.INode
import org.modelix.model.api.getInstantiatableSubConcepts

interface ICellAction {
    fun isApplicable(): Boolean
    fun execute(editor: EditorComponent)
}

interface ITextChangeAction {
    fun isValid(value: String?): Boolean
    fun replaceText(editor: EditorComponent, range: IntRange, replacement: String, newText: String): Boolean
}

class CompositeTextChangeAction(val actions: List<ITextChangeAction>) : ITextChangeAction {
    override fun isValid(value: String?): Boolean {
        return actions.any { it.isValid(value) }
    }

    override fun replaceText(editor: EditorComponent, range: IntRange, replacement: String, newText: String): Boolean {
        return actions
            .filter { it.isValid(newText) }
            .any { it.replaceText(editor, range, replacement, newText) }
    }

    companion object {
        fun create(actions: List<ITextChangeAction?>): ITextChangeAction? {
            val nonNullActions = actions.filterNotNull()
            return when (nonNullActions.size) {
                0 -> null
                1 -> nonNullActions.first()
                else -> CompositeTextChangeAction(nonNullActions)
            }
        }
    }
}

object CellActionProperties {
    val substitute = CellPropertyKey<ICodeCompletionActionProvider?>("substitute", null)
    val transformBefore = CellPropertyKey<ICodeCompletionActionProvider?>("transformBefore", null)
    val transformAfter = CellPropertyKey<ICodeCompletionActionProvider?>("transformAfter", null)
    val insert = CellPropertyKey<ICellAction?>("insert", null)
    val replaceText = CellPropertyKey<ITextChangeAction?>("replaceText", null)
}

class SideTransformNode(val before: Boolean, val node: INode) : ICodeCompletionActionProvider {
    override fun getApplicableActions(parameters: CodeCompletionParameters): List<IActionOrProvider> {
        val engine = parameters.editor.engine ?: return emptyList()
        val location = ExistingNode(node)
        val expectedConcept = location.expectedConcept() ?: return emptyList()
        val allowedConcepts = expectedConcept.getInstantiatableSubConcepts()
        val cellModels = allowedConcepts.map { concept ->
            engine.createCellModel(concept)
        }
        return cellModels.flatMap {
            it.getSideTransformActions(before, node) ?: emptyList()
        }
    }
}

fun Cell.getSubstituteActions() = collectSubstituteActionsBetween(previousLeaf { it.isVisible() }, firstLeaf()).distinct() // TODO non-leafs can also be visible (text cells can have children)
fun Cell.getActionsBefore() = collectInsertActionsBetween(previousLeaf { it.isVisible() }, firstLeaf()).distinct() // TODO non-leafs can also be visible (text cells can have children)
fun Cell.getActionsAfter() = collectInsertActionsBetween(lastLeaf(), nextLeaf { it.isVisible() }).distinct() // TODO non-leafs can also be visible (text cells can have children)

private fun Cell.collectSubstituteActionsBetween(leftLeaf: Cell?, rightLeaf: Cell?): Sequence<ICodeCompletionActionProvider> {
    return collectActionsBetween(leftLeaf, rightLeaf) { cellsFullyBetween, cellsEndingBetween, cellsBeginningBetween ->
        cellsFullyBetween.map { it.getProperty(CellActionProperties.substitute) } +
        cellsBeginningBetween.map { it.getProperty(CellActionProperties.substitute) }
    }
}

private fun Cell.collectInsertActionsBetween(leftLeaf: Cell?, rightLeaf: Cell?): Sequence<ICodeCompletionActionProvider> {
    return collectActionsBetween(leftLeaf, rightLeaf) { cellsFullyBetween, cellsEndingBetween, cellsBeginningBetween ->
        cellsEndingBetween.map { it.getProperty(CellActionProperties.transformAfter) } +
                cellsFullyBetween.flatMap { sequenceOf(
                    it.getProperty(CellActionProperties.transformBefore),
                    it.getProperty(CellActionProperties.transformAfter)
                ) } +
                cellsBeginningBetween.map { it.getProperty(CellActionProperties.transformBefore) }
    }
}

fun <T : Any> collectActionsBetween(
    leftLeaf: Cell?,
    rightLeaf: Cell?,
    actionsAccessor: (
        cellsFullyBetween: List<Cell>,
        cellsEndingBetween: List<Cell>,
        cellsBeginningBetween: List<Cell>,
    ) -> List<T?>
): Sequence<T> {
    require(leftLeaf != null || rightLeaf != null) { "At least one cell is required. Both are null." }
    val commonAncestor: Cell? = leftLeaf?.let { rightLeaf?.commonAncestor(it) }
    val leafsBetween = if (leftLeaf != null && rightLeaf != null) {
        leftLeaf.nextLeafs(false).takeWhile { it != rightLeaf }
    } else if (leftLeaf != null) {
        leftLeaf.nextLeafs(false)
    } else {
        rightLeaf!!.previousLeafs(false)
    }
    val cellsFullyBetween = leafsBetween.map { leaf -> leaf.ancestors(true).takeWhile { it != commonAncestor } }.flatten()
    val cellsEndingBetween = if (leftLeaf == null) emptySequence() else leftLeaf.ancestors(true).takeWhile { it != commonAncestor }
    val cellsBeginningBetween = if (rightLeaf == null) emptySequence() else rightLeaf.ancestors(true).takeWhile { it != commonAncestor }
    return actionsAccessor(cellsFullyBetween.toList(), cellsEndingBetween.toList(), cellsBeginningBetween.toList()).filterNotNull().asSequence()
}
