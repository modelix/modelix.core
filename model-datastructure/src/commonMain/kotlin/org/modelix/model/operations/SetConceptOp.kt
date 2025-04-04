package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModel

/**
 * Operation to set the concept of a node.
 *
 * @param nodeId id of the node
 * @param concept reference to the new concept, or null to remove the concept
 */
class SetConceptOp(val nodeId: INodeReference, val concept: ConceptReference?) : AbstractOperation(), IOperationIntend {

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val node = tree.asModel().resolveNode(nodeId.toGlobal(tree.getId()))
        val originalConcept = node.getConceptReference()
        node.changeConcept(concept ?: NullConcept.getReference())
        return Applied(originalConcept)
    }

    override fun toString(): String {
        return "SetConceptOp $nodeId concept: $concept"
    }

    override fun getOriginalOp(): IOperation = this

    override fun restoreIntend(tree: IModelTree): List<IOperation> {
        return if (tree.containsNode(nodeId.toGlobal(tree.getId())).getBlocking()) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: IModelTree) = this

    inner class Applied(private val originalConcept: ConceptReference) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp(): IOperation = this@SetConceptOp

        override fun invert(): List<IOperation> = listOf(SetConceptOp(nodeId, originalConcept))

        override fun toString(): String = "${super.toString()}, originalConcept: $originalConcept"
    }
}
