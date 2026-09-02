package org.modelix.model.lazy

import org.modelix.datastructures.objects.Object
import org.modelix.model.api.INodeReference
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
import org.modelix.model.persistent.CPTree
import org.modelix.streams.getBlocking

class OperationsCompressor(val resultTree: Object<CPTree>) {

    /**
     * Optimize for bulk imports
     * If a whole subtree is imported then there are a lot of operations where only the AddNewChildOp for the subtree
     * root has the potential to cause any conflict.
     * In that case we replace all of these operation with one AddNewChildSubtreeOp that references the resulting
     * subtree in the new version. We don't lose any information and can reconstruct the original operations if needed.
     */
    fun compressOperations(ops: Array<IOperation>): Array<IOperation> {
        if (ops.size <= CLVersion.INLINED_OPS_LIMIT) return ops

        val compressedOps: MutableList<IOperation> = ArrayList()
        val createdNodes: MutableSet<INodeReference> = HashSet()

        for (op in ops) {
            when (op) {
                is UndoOp, is RevertToOp, is AddNewChildSubtreeOp, is DeleteNodeOp, is MoveNodeOp, is BulkUpdateOp -> return ops
                is NoOp -> {}
                // AddNewChildOp is a subclass of AddNewChildrenOp, so this branch handles both. Each created
                // subtree root is replaced by an AddNewChildSubtreeOp. Children whose parent is itself a freshly
                // created node are already part of an enclosing compressed subtree and only need to be remembered.
                is AddNewChildrenOp -> {
                    if (!createdNodes.contains(op.position.nodeId)) {
                        op.childIdsAndConcepts.forEachIndexed { index, (childId, concept) ->
                            val childPosition = if (op.position.index < 0) {
                                op.position
                            } else {
                                op.position.withIndex(op.position.index + index)
                            }
                            compressedOps += AddNewChildSubtreeOp(resultTree.ref, childPosition, childId, concept)
                        }
                    }
                    op.childIdsAndConcepts.forEach { createdNodes.add(it.first) }
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
            if (!resultTree.data.getModelTree().containsNode(id).getBlocking(resultTree.graph)) {
                throw RuntimeException("Tree expected to contain node $id")
            }
        }

        // if we save less than 10 operations then it's probably not worth doing the replacement
        return if (ops.size - compressedOps.size >= 10) compressedOps.toTypedArray() else ops
    }
}
