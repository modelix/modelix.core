package org.modelix.model.mutable

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId
import org.modelix.model.api.IModel
import org.modelix.model.api.INodeReference

class SingleThreadMutableModelTree<NodeId>(
    override var tree: IModelTree<NodeId>,
    private val idGenerator: INodeIdGenerator<NodeId>,
) : IMutableModelTree<NodeId>, IMutableModelTree.WriteTransaction<NodeId> {
    override fun getId(): TreeId {
        return tree.getId()
    }

    override fun getIdGenerator(): INodeIdGenerator<NodeId> {
        return idGenerator
    }

    override fun <R> runRead(body: (IMutableModelTree.Transaction<NodeId>) -> R): R {
        return body(this)
    }

    override fun <R> runWrite(body: (IMutableModelTree.WriteTransaction<NodeId>) -> R): R {
        return body(this)
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return true
    }

    override fun getTransaction(): IMutableModelTree.Transaction<NodeId> {
        return this
    }

    override fun getWriteTransaction(): IMutableModelTree.WriteTransaction<NodeId> {
        return this
    }

    override fun addListener(listener: IMutableModelTree.Listener<NodeId>) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(listener: IMutableModelTree.Listener<NodeId>) {
        throw UnsupportedOperationException()
    }

    override fun mutate(parameters: MutationParameters<NodeId>) {
        tree = tree.mutate(parameters).getBlocking()
    }
}

fun IModelTree<INodeReference>.asModel(): IModel = SingleThreadMutableModelTree(this, DummyIdGenerator()).asModel()
