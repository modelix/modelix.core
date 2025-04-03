package org.modelix.model.mutable

import mu.KotlinLogging
import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.kotlin.utils.ContextValue
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.model.TreeId
import org.modelix.streams.query

class ThreadSafeMutableModelTree<NodeId>(
    private var tree: IModelTree<NodeId>,
    private val idGenerator: INodeIdGenerator<NodeId>,
) : IMutableModelTree<NodeId> {
    private val LOG = KotlinLogging.logger {}
    private val writeLock = Any()
    private val contextTransaction = ContextValue<IMutableModelTree.Transaction<NodeId>>()
    private var listeners: Set<IMutableModelTree.Listener<NodeId>> = emptySet()

    override fun getId(): TreeId {
        return tree.getId()
    }

    override fun getIdGenerator(): INodeIdGenerator<NodeId> {
        return idGenerator
    }

    private fun <R> runWithTransaction(transaction: IMutableModelTree.Transaction<NodeId>, body: () -> R): R {
        // TODO make visible in INodeResolutionScope.contextScope
        return contextTransaction.computeWith(transaction, body)
    }

    override fun <R> runRead(body: (IMutableModelTree.Transaction<NodeId>) -> R): R {
        val prevTransaction = contextTransaction.getValueOrNull()
        return if (prevTransaction != null && prevTransaction !is IMutableModelTree.WriteTransaction) {
            body(prevTransaction)
        } else {
            val currentTree = prevTransaction?.tree ?: tree
            val t = ReadTransactionImpl(currentTree)
            runWithTransaction(t) { body(t) }
        }
    }

    override fun <R> runWrite(body: (IMutableModelTree.WriteTransaction<NodeId>) -> R): R {
        runSynchronized(writeLock) {
            val prevTransaction = contextTransaction.getValueOrNull()
            check(prevTransaction !is ReadTransactionImpl) { "Cannot run write from read" }
            val prevWrite = prevTransaction as WriteTransactionImpl<NodeId>?
            val oldTree = prevWrite?.tree ?: tree
            val newWrite = WriteTransactionImpl<NodeId>(oldTree)
            val result = runWithTransaction(newWrite) { body(newWrite) }
            val newTree = newWrite.tree
            if (prevWrite == null) {
                tree = newTree
                notifyTreeChange(oldTree, newTree)
            } else {
                prevWrite.tree = newTree
            }
            return result
        }
    }

    override fun canRead(): Boolean {
        return contextTransaction.getValueOrNull() != null
    }

    override fun canWrite(): Boolean {
        return contextTransaction.getValueOrNull() is WriteTransactionImpl
    }

    override fun getTransaction(): IMutableModelTree.Transaction<NodeId> {
        return contextTransaction.getValueOrNull() ?: throw IllegalStateException("Not in a transaction")
    }

    override fun getWriteTransaction(): IMutableModelTree.WriteTransaction<NodeId> {
        val transaction = getTransaction()
        check(transaction is WriteTransactionImpl<NodeId>) { "Not in a write transaction" }
        return transaction
    }

    override fun addListener(listener: IMutableModelTree.Listener<NodeId>) {
        listeners += listener
    }

    override fun removeListener(listener: IMutableModelTree.Listener<NodeId>) {
        listeners -= listener
    }

    private fun notifyTreeChange(oldTree: IModelTree<NodeId>, newTree: IModelTree<NodeId>) {
        if (oldTree === newTree) {
            return
        }
        for (l in listeners) {
            try {
                l.treeChanged(oldTree, newTree)
            } catch (ex: Throwable) {
                LOG.error(ex) { "Exception in listener" }
            }
        }
    }

    private class ReadTransactionImpl<NodeId>(override val tree: IModelTree<NodeId>) : IMutableModelTree.Transaction<NodeId>

    private class WriteTransactionImpl<NodeId>(override var tree: IModelTree<NodeId>) : IMutableModelTree.WriteTransaction<NodeId> {
        override fun mutate(parameters: MutationParameters<NodeId>) {
            tree = tree.asObject().graph.query { tree.mutate(parameters) }
        }
    }
}
