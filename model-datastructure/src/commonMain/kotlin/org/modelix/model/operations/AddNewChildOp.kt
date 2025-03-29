package org.modelix.model.operations

import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.persistent.SerializationUtil

class AddNewChildOp(position: PositionInRole, childId: Long, concept: IConceptReference?) : AddNewChildrenOp(position, longArrayOf(childId), arrayOf(concept)) {
    val childId: Long get() = childIds[0]
    val concept: IConceptReference? get() = concepts[0]

    fun withConcept(newConcept: IConceptReference?): AddNewChildOp {
        return if (concept == newConcept) this else AddNewChildOp(position, childId, newConcept)
    }

    override fun withPosition(newPos: PositionInRole): AddNewChildOp {
        return if (newPos == position) this else AddNewChildOp(newPos, childId, concept)
    }

    override fun toString(): String {
        return "AddNewChildOp ${SerializationUtil.longToHex(childId)}, $position, $concept"
    }
}

open class AddNewChildrenOp(val position: PositionInRole, val childIds: LongArray, val concepts: Array<IConceptReference?>) : AbstractOperation() {

    open fun withPosition(newPos: PositionInRole): AddNewChildrenOp {
        return if (newPos == position) this else AddNewChildrenOp(newPos, childIds, concepts)
    }

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        transaction.addNewChildren(position.nodeId, position.role.stringForLegacyApi(), position.index, childIds, concepts)
        return Applied()
    }

    override fun toString(): String {
        return "AddNewChildrenOp ${childIds.map { SerializationUtil.longToHex(it) }}, $position, ${concepts.map { it?.getUID() }}"
    }

    inner class Applied : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@AddNewChildrenOp

        override fun invert(): List<IOperation> {
            return childIds.map { DeleteNodeOp(it) }
        }
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        val children = tree.getChildren(position.nodeId, position.role.stringForLegacyApi())
        return Intend(
            CapturedInsertPosition(position.index, children.toList().toLongArray()),
        )
    }

    inner class Intend(val capturedPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            if (tree.containsNode(position.nodeId)) {
                val newIndex = capturedPosition.findIndex(tree.getChildren(position.nodeId, position.role.stringForLegacyApi()).toList().toLongArray())
                return listOf(withPosition(position.withIndex(newIndex)))
            } else {
                return listOf(withPosition(getDetachedNodesEndPosition(tree)))
            }
        }

        override fun getOriginalOp() = this@AddNewChildrenOp
    }
}
