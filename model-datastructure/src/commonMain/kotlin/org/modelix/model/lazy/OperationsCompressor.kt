package org.modelix.model.lazy

import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.AddNewChildSubtreeOp
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.BulkUpdateOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.NoOp
import org.modelix.model.operations.RevertToOp
import org.modelix.model.operations.SetConceptOp
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.operations.UndoOp

class OperationsCompressor(val resultTree: CLTree) {

    /**
     * Optimize for bulk imports
     * If a whole subtree is imported then there are a lot of operations where only the AddNewChildOp for the subtree
     * root has the potential to cause any conflict.
     * In that case we replace all of these operation with one AddNewChildSubtreeOp that references the resulting
     * subtree in the new version. We don't lose any information and can reconstruct the original operations if needed.
     */
    fun compressOperations(ops: Array<IOperation>): Array<IOperation> {
        if (ops.size <= CLVersion.INLINED_OPS_LIMIT) return ops

        val resultTreeRef = resultTree.resolvedData.ref
        val compressedOps: MutableList<IOperation> = ArrayList()
        val createdNodes: MutableSet<Long> = HashSet()

        for (op in ops) {
            when (op) {
                is UndoOp, is RevertToOp, is AddNewChildSubtreeOp, is DeleteNodeOp, is MoveNodeOp, is BulkUpdateOp, is AddNewChildrenOp -> return ops
                is NoOp -> {}
                is AddNewChildOp -> {
                    if (!createdNodes.contains(op.position.nodeId)) {
                        compressedOps += AddNewChildSubtreeOp(resultTreeRef, op.position, op.childId, op.concept)
                    }
                    createdNodes.add(op.childId)
                }
                is SetPropertyOp -> {
                    if (!createdNodes.contains(op.nodeId)) compressedOps += op
                }
                is SetConceptOp -> {
                    if (!createdNodes.contains(op.nodeId)) compressedOps += op
                }
                is SetReferenceOp -> {
                    if (!createdNodes.contains(op.sourceId)) compressedOps += op
                }
            }
        }

        for (id in createdNodes) {
            if (!resultTree.containsNode(id)) throw RuntimeException("Tree expected to contain node $id")
        }

        // if we save less than 10 operations then it's probably not worth doing the replacement
        return if (ops.size - compressedOps.size >= 10) compressedOps.toTypedArray() else ops
    }
}
