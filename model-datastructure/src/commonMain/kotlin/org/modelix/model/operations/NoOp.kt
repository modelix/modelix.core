package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.model.mutable.IMutableModelTree

class NoOp : AbstractOperation(), IAppliedOperation, IOperationIntend {
    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        return this
    }

    override fun invert(): List<IOperation> {
        return listOf(this)
    }

    override fun toString(): String {
        return "NoOp"
    }

    override fun captureIntend(tree: IModelTree) = this

    override fun getOriginalOp() = this

    override fun restoreIntend(tree: IModelTree) = listOf(this)
}
