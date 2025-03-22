package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLVersion
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.ObjectReference
import org.modelix.model.persistent.CPVersion

class UndoOp(val versionHash: ObjectReference<CPVersion>) : AbstractOperation() {
    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(versionHash)

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        return Applied(
            captureIntend(transaction.tree)
                .restoreIntend(transaction.tree)
                .map { it.apply(transaction) },
        )
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        val versionToUndo = CLVersion(versionHash.resolveLater().query())
        val originalAppliedOps = getAppliedOps(versionToUndo)
        val invertedOps = originalAppliedOps.reversed().flatMap { it.invert() }
        val invertedOpIntends = captureIntend(versionToUndo.getTree(), invertedOps)
        return Intend(invertedOpIntends)
    }

    private fun getAppliedOps(version: CLVersion): List<IAppliedOperation> {
        val tree = version.baseVersion!!.getTree()
        val branch = TreePointer(tree)
        return branch.computeWrite {
            version.operations.map { it.apply(branch.writeTransaction) }
        }
    }

    private fun captureIntend(tree: ITree, ops: List<IOperation>): List<IOperationIntend> {
        val branch = TreePointer(tree)
        return branch.computeWrite {
            ops.map {
                val intend = it.captureIntend(branch.transaction.tree)
                it.apply(branch.writeTransaction)
                intend
            }
        }
    }

    private fun restoreIntend(tree: ITree, opIntends: List<IOperationIntend>): List<IOperation> {
        val branch = TreePointer(tree)
        return branch.computeWrite {
            opIntends.flatMap {
                val restoredOps = it.restoreIntend(branch.transaction.tree)
                restoredOps.forEach { restoredOp -> restoredOp.apply(branch.writeTransaction) }
                restoredOps
            }
        }
    }

    override fun toString(): String {
        return "UndoOp ${versionHash.getHash()}"
    }

    inner class Intend(val intends: List<IOperationIntend>) : IOperationIntend {
        override fun getOriginalOp(): IOperation {
            return this@UndoOp
        }

        override fun restoreIntend(tree: ITree): List<IOperation> {
            return restoreIntend(tree, intends)
        }
    }

    inner class Applied(val appliedOps: List<IAppliedOperation>) : IAppliedOperation {
        override fun getOriginalOp(): IOperation {
            return this@UndoOp
        }

        override fun invert(): List<IOperation> {
            return appliedOps.reversed().flatMap { it.invert() }
        }
    }
}
