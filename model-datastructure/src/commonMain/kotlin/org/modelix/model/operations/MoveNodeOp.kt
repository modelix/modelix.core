package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction

class MoveNodeOp(val childId: Long, val targetPosition: PositionInRole) : AbstractOperation() {
    fun withPos(newTarget: PositionInRole): MoveNodeOp {
        return if (newTarget == targetPosition) {
            this
        } else {
            MoveNodeOp(childId, newTarget)
        }
    }

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        val sourcePosition = getNodePosition(transaction.tree, childId)
        transaction.moveChild(targetPosition.nodeId, targetPosition.role, targetPosition.index, childId)
        return Applied(sourcePosition)
    }

    override fun toString(): String {
        return "MoveNodeOp ${childId.toString(16)}->$targetPosition"
    }

    inner class Applied(val sourcePosition: PositionInRole) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@MoveNodeOp

        override fun invert(): List<IOperation> {
            return listOf(MoveNodeOp(childId, sourcePosition))
        }

        override fun toString(): String {
            return "applied:MoveNodeOp ${childId.toString(16)}: $sourcePosition->$targetPosition"
        }
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        val capturedTargetPosition = CapturedInsertPosition(
            targetPosition.index,
            tree.getChildren(targetPosition.nodeId, targetPosition.role).toList().toLongArray(),
        )

        return Intend(capturedTargetPosition)
    }

    inner class Intend(val capturedTargetPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            if (!tree.containsNode(childId)) return listOf(NoOp())
            val newSourcePosition = getNodePosition(tree, childId)
            if (!tree.containsNode(targetPosition.nodeId)) {
                return listOf(
                    withPos(getDetachedNodesEndPosition(tree)),
                )
            }
            if (getAncestors(tree, targetPosition.nodeId).contains(childId)) return listOf(NoOp())
            val newTargetPosition = if (tree.containsNode(targetPosition.nodeId)) {
                val newTargetIndex = capturedTargetPosition.findIndex(tree.getChildren(targetPosition.nodeId, targetPosition.role).toList().toLongArray())
                targetPosition.withIndex(newTargetIndex)
            } else {
                getDetachedNodesEndPosition(tree)
            }
            return listOf(withPos(newTargetPosition))
        }

        override fun getOriginalOp() = this@MoveNodeOp
    }
}
