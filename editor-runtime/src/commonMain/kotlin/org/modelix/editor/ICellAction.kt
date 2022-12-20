package org.modelix.editor

interface ICellAction {

}

interface ITextChangeAction: ICellAction {
    fun replaceText(editor: EditorComponent, range: IntRange, replacement: String): Boolean
}

object CellActionProperties {
    val substitute = CellPropertyKey<ICodeCompletionActionProvider?>("substitute", null)
    val insertBefore = CellPropertyKey<ICodeCompletionActionProvider?>("insertBefore", null)
    val insertAfter = CellPropertyKey<ICodeCompletionActionProvider?>("insertAfter", null)
    val replaceText = CellPropertyKey<ITextChangeAction?>("replaceText", null)
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
        cellsEndingBetween.map { it.getProperty(CellActionProperties.insertAfter) } +
                cellsFullyBetween.flatMap { sequenceOf(
                    it.getProperty(CellActionProperties.insertBefore),
                    it.getProperty(CellActionProperties.insertAfter)
                ) } +
                cellsBeginningBetween.map { it.getProperty(CellActionProperties.insertBefore) }
    }
}

private fun Cell.collectActionsBetween(
    leftLeaf: Cell?,
    rightLeaf: Cell?,
    actionsAccessor: (
        cellsFullyBetween: List<Cell>,
        cellsEndingBetween: List<Cell>,
        cellsBeginningBetween: List<Cell>,
    ) -> List<ICodeCompletionActionProvider?>
): Sequence<ICodeCompletionActionProvider> {
    require(leftLeaf != null || rightLeaf != null) { "At least one cell is required. Both are null." }
    val commonAncestor: Cell? = leftLeaf?.let { rightLeaf?.commonAncestor(it) }
    val leafsBetween = if (leftLeaf != null && rightLeaf != null) {
        leftLeaf.nextLeafs().takeWhile { it != rightLeaf }
    } else if (leftLeaf != null) {
        leftLeaf.nextLeafs()
    } else {
        rightLeaf!!.previousLeafs()
    }
    val cellsFullyBetween = leafsBetween.map { leaf -> leaf.ancestors(true).takeWhile { it != commonAncestor } }.flatten()
    val cellsEndingBetween = if (leftLeaf == null) emptySequence() else leftLeaf.ancestors(true).takeWhile { it != commonAncestor }
    val cellsBeginningBetween = if (rightLeaf == null) emptySequence() else rightLeaf.ancestors(true).takeWhile { it != commonAncestor }
    return actionsAccessor(cellsFullyBetween.toList(), cellsEndingBetween.toList(), cellsBeginningBetween.toList()).filterNotNull().asSequence()
}
