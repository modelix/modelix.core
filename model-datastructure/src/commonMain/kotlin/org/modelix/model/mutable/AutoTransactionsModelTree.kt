package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId

class AutoTransactionsModelTree<NodeId>(val mutableTree: IGenericMutableModelTree<NodeId>) : IGenericMutableModelTree<NodeId> {
    private fun isInTransaction() = mutableTree.canRead()

    override fun <R> runRead(body: (IGenericMutableModelTree.Transaction<NodeId>) -> R): R {
        return mutableTree.runRead(body)
    }

    override fun getId(): TreeId {
        return mutableTree.getId()
    }

    override fun getIdGenerator(): INodeIdGenerator<NodeId> {
        return mutableTree.getIdGenerator()
    }

    override fun <R> runWrite(body: (IGenericMutableModelTree.WriteTransaction<NodeId>) -> R): R {
        return mutableTree.runWrite(body)
    }

    override fun canRead(): Boolean {
        return mutableTree.canRead()
    }

    override fun canWrite(): Boolean {
        return mutableTree.canWrite()
    }

    override fun getTransaction(): IGenericMutableModelTree.Transaction<NodeId> {
        return if (isInTransaction()) mutableTree.getTransaction() else AutoReadTransaction()
    }

    override fun getWriteTransaction(): IGenericMutableModelTree.WriteTransaction<NodeId> {
        return if (isInTransaction()) mutableTree.getWriteTransaction() else AutoWriteTransaction()
    }

    override fun addListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        mutableTree.addListener(listener)
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        mutableTree.removeListener(listener)
    }

    inner class AutoReadTransaction : IGenericMutableModelTree.Transaction<NodeId> {
        override val tree: IGenericModelTree<NodeId>
            get() = mutableTree.runRead { it.tree }
    }

    inner class AutoWriteTransaction : IGenericMutableModelTree.WriteTransaction<NodeId> {
        override fun mutate(parameters: MutationParameters<NodeId>) {
            mutableTree.runWrite { it.mutate(parameters) }
        }

        override var tree: IGenericModelTree<NodeId>
            get() = mutableTree.runRead { it.tree }
            set(value) { mutableTree.runWrite { it.tree = value } }
    }
}

fun <NodeId> IGenericMutableModelTree<NodeId>.withAutoTransactions(): IGenericMutableModelTree<NodeId> {
    return AutoTransactionsModelTree(this)
}
