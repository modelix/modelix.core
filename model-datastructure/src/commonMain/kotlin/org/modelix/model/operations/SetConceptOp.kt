package org.modelix.model.operations

import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.persistent.SerializationUtil

/**
 * Operation to set the concept of a node.
 *
 * @param nodeId id of the node
 * @param concept reference to the new concept, or null to remove the concept
 */
class SetConceptOp(val nodeId: Long, val concept: IConceptReference?) : AbstractOperation(), IOperationIntend {
    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        val originalConcept = transaction.getConceptReference(nodeId)
        transaction.setConcept(nodeId, concept)
        return Applied(originalConcept)
    }

    override fun toString(): String {
        return "SetConceptOp ${SerializationUtil.longToHex(nodeId)} concept: $concept"
    }

    override fun getOriginalOp(): IOperation = this

    override fun restoreIntend(tree: ITree): List<IOperation> {
        return if (tree.containsNode(nodeId)) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: ITree) = this

    inner class Applied(private val originalConcept: IConceptReference?) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp(): IOperation = this@SetConceptOp

        override fun invert(): List<IOperation> = listOf(SetConceptOp(nodeId, originalConcept))

        override fun toString(): String = "${super.toString()}, originalConcept: $originalConcept"
    }
}
