package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.toGlobal
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.persistent.CPTree

class BulkUpdateOp(
    val resultTreeHash: ObjectReference<CPTree>,
    val subtreeRootId: INodeReference,
) : AbstractOperation() {

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(resultTreeHash)

    /**
     * Since this operation is recorded at the end of a bulk update we need to create an IAppliedOperation without
     * actually applying it again.
     */
    fun afterApply(baseTree: IModelTree) = Applied(baseTree)

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val resultTree = getResultTree()
        if (subtreeRootId.toGlobal(tree.getId()) != resultTree.getRootNodeId()) {
            throw UnsupportedOperationException(
                "Updating a subtree is not supported yet. " +
                    "Bulk update must be executed on the root node. [subtreeRootId=$subtreeRootId]",
            )
        }
        tree.getWriteTransaction().tree = resultTree
        return Applied(resultTree)
    }

    private fun getResultTree(): IModelTree = resultTreeHash.resolveNow().data.getModelTree()

    override fun toString(): String {
        return "BulkUpdateOp ${resultTreeHash.getHash()}, $subtreeRootId"
    }

    inner class Applied(val baseTree: IModelTree) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@BulkUpdateOp

        override fun invert(): List<IOperation> {
            return listOf(BulkUpdateOp(baseTree.asObject().ref, subtreeRootId))
        }
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        return Intend()
    }

    inner class Intend : IOperationIntend {
        override fun restoreIntend(tree: IModelTree): List<IOperation> {
            // The intended change is to put the model into the given state. Any concurrent change can just be
            // overwritten as long as the subtree root as the starting point still exists.
            return if (tree.containsNode(subtreeRootId.toGlobal(tree.getId())).getBlocking()) {
                listOf(getOriginalOp())
            } else {
                listOf(NoOp())
            }
        }

        override fun getOriginalOp() = this@BulkUpdateOp
    }
}
