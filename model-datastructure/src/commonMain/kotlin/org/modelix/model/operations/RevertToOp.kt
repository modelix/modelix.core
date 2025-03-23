package org.modelix.model.operations

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.VersionMerger
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLVersion
import org.modelix.model.persistent.CPVersion

class RevertToOp(val latestKnownVersionRef: ObjectReference<CPVersion>, val versionToRevertToRef: ObjectReference<CPVersion>) : AbstractOperation() {
    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(latestKnownVersionRef, versionToRevertToRef)

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        return Applied(
            captureIntend(transaction.tree)
                .restoreIntend(transaction.tree)
                .map { it.apply(transaction) },
        )
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        return Intend(captureIntend(tree, collectUndoOps()))
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

    private fun collectUndoOps(): List<IOperation> {
        val latestKnownVersion = CLVersion(latestKnownVersionRef.resolveLater().query())
        val versionToRevertTo = CLVersion(versionToRevertToRef.resolveLater().query())
        val result = mutableListOf<IOperation>()
        val commonBase = VersionMerger.commonBaseVersion(latestKnownVersion, versionToRevertTo)
        result += getPath(latestKnownVersion, commonBase).map { UndoOp(it.resolvedData.ref) }
        if (commonBase == null || commonBase.hash != versionToRevertTo.hash) {
            // redo operations on a branch
            result += getPath(versionToRevertTo, commonBase).reversed().flatMap { it.operations }
        }
        return result
    }

    private fun getPath(newerVersion: CLVersion, olderVersionExclusive: CLVersion?): List<CLVersion> {
        val result = mutableListOf<CLVersion>()
        var v = newerVersion
        while (olderVersionExclusive == null || v.hash != olderVersionExclusive.hash) {
            result += v
            v = v.baseVersion ?: break
        }
        return result
    }

    override fun toString(): String {
        return "RevertToOp $latestKnownVersionRef -> $versionToRevertToRef"
    }

    inner class Intend(val intends: List<IOperationIntend>) : IOperationIntend {
        override fun getOriginalOp(): IOperation {
            return this@RevertToOp
        }

        override fun restoreIntend(tree: ITree): List<IOperation> {
            return restoreIntend(tree, intends)
        }
    }

    inner class Applied(val appliedOps: List<IAppliedOperation>) : IAppliedOperation {
        override fun getOriginalOp(): IOperation {
            return this@RevertToOp
        }

        override fun invert(): List<IOperation> {
            return appliedOps.reversed().flatMap { it.invert() }
        }
    }
}
