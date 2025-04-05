package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.INodeReference
import org.modelix.model.api.remove
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModel
import org.modelix.streams.getBlocking

/**
 * Deletes the node and all its descendants.
 */
class DeleteNodeOp(val childId: INodeReference) : AbstractOperation(), IOperationIntend {
    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val treeBeforeDelete = tree.getTransaction().tree
        val node = tree.asModel().resolveNode(childId.toGlobal(tree.getId()))
        val concept = node.getConceptReference()
        val position = getNodePosition(node)
        node.remove()
        return Applied(
            AddNewChildSubtreeOp(
                treeBeforeDelete.asObject().ref,
                position,
                childId,
                concept,
            ),
        )
    }

    override fun toString(): String {
        return "DeleteNodeOp $childId"
    }

    override fun restoreIntend(tree: IModelTree): List<IOperation> {
        if (!tree.containsNode(childId.toGlobal(tree.getId())).getBlocking(tree)) return listOf(NoOp())
        val allChildren = tree.getChildren(childId.toGlobal(tree.getId())).toList().getBlocking(tree)
        if (allChildren.isNotEmpty()) {
            val targetPos = getDetachedNodesEndPosition(tree)
            return allChildren
                .reversed()
                .map { MoveNodeOp(it, targetPos) }
                .plus(this)
        }
        return listOf(this)
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        return this
    }

    override fun getOriginalOp() = this

    inner class Applied(
        val inverseOp: AddNewChildSubtreeOp,
    ) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@DeleteNodeOp

        override fun invert(): List<IOperation> {
            return listOf(inverseOp)
        }
    }
}
