package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.CLTree
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.ObjectReference
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.SerializationUtil

class BulkUpdateOp(
    val resultTreeHash: ObjectReference<CPTree>,
    val subtreeRootId: Long,
) : AbstractOperation() {

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(resultTreeHash)

    /**
     * Since this operation is recorded at the end of a bulk update we need to create an IAppliedOperation without
     * actually applying it again.
     */
    fun afterApply(baseTree: CLTree) = Applied(baseTree)

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        val baseTree = transaction.tree as CLTree
        val resultTree = getResultTree()
        TODO("Change the (sub)tree so that it is identical to the resultTree")
        return Applied(baseTree)
    }

    private fun getResultTree(): CLTree = CLTree(resultTreeHash.resolveLater().query())

    override fun toString(): String {
        return "BulkUpdateOp ${resultTreeHash.getHash()}, ${SerializationUtil.longToHex(subtreeRootId)}"
    }

    inner class Applied(val baseTree: CLTree) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@BulkUpdateOp

        override fun invert(): List<IOperation> {
            return listOf(BulkUpdateOp(baseTree.resolvedData.ref, subtreeRootId))
        }
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        return Intend()
    }

    inner class Intend : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            // The intended change is to put the model into the given state. Any concurrent change can just be
            // overwritten as long as the subtree root as the starting point still exists.
            return if (tree.containsNode(subtreeRootId)) {
                listOf(getOriginalOp())
            } else {
                listOf(NoOp())
            }
        }

        override fun getOriginalOp() = this@BulkUpdateOp
    }
}
