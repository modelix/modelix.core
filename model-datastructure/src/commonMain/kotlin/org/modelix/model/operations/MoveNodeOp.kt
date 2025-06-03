package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.getAncestors
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModel
import org.modelix.streams.contains
import org.modelix.streams.getBlocking

class MoveNodeOp(val childId: INodeReference, val targetPosition: PositionInRole) : AbstractOperation() {
    fun withPos(newTarget: PositionInRole): MoveNodeOp {
        return if (newTarget == targetPosition) {
            this
        } else {
            MoveNodeOp(childId, newTarget)
        }
    }

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val childNode = tree.asModel().resolveNode(childId.toGlobal(tree.getId()))
        val sourcePosition = getNodePosition(childNode)
        tree.asModel().resolveNode(targetPosition.nodeId.toGlobal(tree.getId())).moveChild(targetPosition.role, targetPosition.index, childNode)
        return Applied(sourcePosition)
    }

    override fun toString(): String {
        return "MoveNodeOp $childId->$targetPosition"
    }

    inner class Applied(val sourcePosition: PositionInRole) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@MoveNodeOp

        override fun invert(): List<IOperation> {
            return listOf(MoveNodeOp(childId, sourcePosition))
        }

        override fun toString(): String {
            return "applied:MoveNodeOp $childId: $sourcePosition->$targetPosition"
        }
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        val capturedTargetPosition = CapturedInsertPosition(
            targetPosition.index,
            tree.getChildren(targetPosition.nodeId.toGlobal(tree.getId()), targetPosition.role).toList().getBlocking(tree),
        )
        return Intend(capturedTargetPosition)
    }

    inner class Intend(val capturedTargetPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: IModelTree): List<IOperation> {
            if (!tree.containsNode(childId.toGlobal(tree.getId())).getBlocking(tree)) return emptyList()
            val newSourcePosition = getNodePosition(tree, childId.toGlobal(tree.getId()))
            if (!tree.containsNode(targetPosition.nodeId.toGlobal(tree.getId())).getBlocking(tree)) {
                return listOf(
                    withPos(getDetachedNodesEndPosition(tree)),
                )
            }
            if (tree.getAncestors(targetPosition.nodeId.toGlobal(tree.getId()), false).contains(childId.toGlobal(tree.getId())).getBlocking(tree)) return emptyList()
            val newTargetPosition = if (tree.containsNode(targetPosition.nodeId.toGlobal(tree.getId())).getBlocking(tree)) {
                val newTargetIndex = capturedTargetPosition.findIndex(
                    tree.getChildren(targetPosition.nodeId.toGlobal(tree.getId()), targetPosition.role).toList().getBlocking(tree),
                    targetPosition.index,
                )
                targetPosition.withIndex(newTargetIndex)
            } else {
                getDetachedNodesEndPosition(tree)
            }
            return listOf(withPos(newTargetPosition))
        }

        override fun getOriginalOp() = this@MoveNodeOp
    }
}
