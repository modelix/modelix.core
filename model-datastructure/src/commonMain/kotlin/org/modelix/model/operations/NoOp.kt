package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.IDeserializingKeyValueStore

class NoOp : AbstractOperation(), IAppliedOperation, IOperationIntend {
    override fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation {
        return this
    }

    override fun invert(): List<IOperation> {
        return listOf(this)
    }

    override fun toString(): String {
        return "NoOp"
    }

    override fun captureIntend(tree: ITree, store: IDeserializingKeyValueStore) = this

    override fun getOriginalOp() = this

    override fun restoreIntend(tree: ITree) = listOf(this)
}
