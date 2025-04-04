package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.VersionMerger
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asMutableSingleThreaded
import org.modelix.model.persistent.CPVersion

class RevertToOp(val latestKnownVersionRef: ObjectReference<CPVersion>, val versionToRevertToRef: ObjectReference<CPVersion>) : AbstractOperation() {
    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(latestKnownVersionRef, versionToRevertToRef)

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        return Applied(
            captureIntend(tree.getTransaction().tree)
                .restoreIntend(tree.getTransaction().tree)
                .map { it.apply(tree) },
        )
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        return Intend(captureIntend(tree, collectUndoOps()))
    }

    private fun captureIntend(tree: IModelTree, ops: List<IOperation>): List<IOperationIntend> {
        val mutableTree = tree.asMutableSingleThreaded()
        return ops.map {
            val intend = it.captureIntend(mutableTree.getTransaction().tree)
            it.apply(mutableTree)
            intend
        }
    }

    private fun restoreIntend(tree: IModelTree, opIntends: List<IOperationIntend>): List<IOperation> {
        val mutableTree = tree.asMutableSingleThreaded()
        return opIntends.flatMap {
            val restoredOps = it.restoreIntend(mutableTree.getTransaction().tree)
            restoredOps.forEach { restoredOp -> restoredOp.apply(mutableTree) }
            restoredOps
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

        override fun restoreIntend(tree: IModelTree): List<IOperation> {
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
