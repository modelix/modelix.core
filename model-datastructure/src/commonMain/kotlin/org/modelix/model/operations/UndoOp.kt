package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asMutableSingleThreaded
import org.modelix.model.persistent.CPVersion

class UndoOp(val versionHash: ObjectReference<CPVersion>) : AbstractOperation() {
    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(versionHash)

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        return Applied(
            captureIntend(tree.getTransaction().tree)
                .restoreIntend(tree.getTransaction().tree)
                .map { it.apply(tree) },
        )
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        val versionToUndo = CLVersion(versionHash.resolveLater().query())
        val originalAppliedOps = getAppliedOps(versionToUndo)
        val invertedOps = originalAppliedOps.reversed().flatMap { it.invert() }
        val invertedOpIntends = captureIntend(versionToUndo.getModelTree(), invertedOps)
        return Intend(invertedOpIntends)
    }

    private fun getAppliedOps(version: CLVersion): List<IAppliedOperation> {
        val tree = version.baseVersion!!.getModelTree()
        val mutableTree = tree.asMutableSingleThreaded()
        return version.operations.map { it.apply(mutableTree) }
    }

    private fun captureIntend(tree: IModelTree, ops: List<IOperation>): List<IOperationIntend> {
        val mutableTree = tree.asMutableSingleThreaded()
        return ops.map { it ->
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

    override fun toString(): String {
        return "UndoOp ${versionHash.getHash()}"
    }

    inner class Intend(val intends: List<IOperationIntend>) : IOperationIntend {
        override fun getOriginalOp(): IOperation {
            return this@UndoOp
        }

        override fun restoreIntend(tree: IModelTree): List<IOperation> {
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
