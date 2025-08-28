package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId

/**
 * Just an adapter when an API expects a [IMutableModelTree], but there aren't any actual changes expected.
 */
class ReadonlyMutableModelTree<NodeId>(val tree: IGenericModelTree<NodeId>) : IGenericMutableModelTree<NodeId> {
    private val transaction = object : IGenericMutableModelTree.WriteTransaction<NodeId> {
        override var tree: IGenericModelTree<NodeId>
            get() = this@ReadonlyMutableModelTree.tree
            set(value) { throwImmutableException() }
        override fun getIdGenerator(): INodeIdGenerator<NodeId> = throwImmutableException()
        override fun mutate(parameters: MutationParameters<NodeId>): Unit = throwImmutableException()
    }

    override fun getId(): TreeId = tree.getId()

    override fun getIdGenerator(): INodeIdGenerator<NodeId> = throwImmutableException()

    override fun <R> runRead(body: (IGenericMutableModelTree.Transaction<NodeId>) -> R): R {
        return body(transaction)
    }

    override fun <R> runWrite(body: (IGenericMutableModelTree.WriteTransaction<NodeId>) -> R): R {
        // Some code may always start a write transaction because it doesn't know what its callee does,
        // so we allow this and throw an exception later when writing is actually attempted.
        return body(transaction)
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return false
    }

    override fun getTransaction(): IGenericMutableModelTree.Transaction<NodeId> = transaction

    override fun getWriteTransaction(): IGenericMutableModelTree.WriteTransaction<NodeId> = transaction

    override fun addListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        // Listener will never be notified, because the tree is read only.
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        // Listener will never be notified, because the tree is read only.
    }

    private fun throwImmutableException(): Nothing = throw IllegalStateException("tree is immutable")
}

fun <T> IGenericModelTree<T>.asMutableReadonly(): IGenericMutableModelTree<T> = ReadonlyMutableModelTree(this)
