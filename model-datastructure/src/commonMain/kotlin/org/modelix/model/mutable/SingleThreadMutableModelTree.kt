package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId
import org.modelix.model.api.IModel
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.streams.getBlocking

class SingleThreadMutableModelTree<NodeId>(
    override var tree: IGenericModelTree<NodeId>,
    private val idGenerator: INodeIdGenerator<NodeId> = DummyIdGenerator(),
) : IGenericMutableModelTree<NodeId>, IGenericMutableModelTree.WriteTransaction<NodeId> {
    override fun getId(): TreeId {
        return tree.getId()
    }

    override fun getIdGenerator(): INodeIdGenerator<NodeId> {
        return idGenerator
    }

    override fun <R> runRead(body: (IGenericMutableModelTree.Transaction<NodeId>) -> R): R {
        return body(this)
    }

    override fun <R> runWrite(body: (IGenericMutableModelTree.WriteTransaction<NodeId>) -> R): R {
        return body(this)
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return true
    }

    override fun getTransaction(): IGenericMutableModelTree.Transaction<NodeId> {
        return this
    }

    override fun getWriteTransaction(): IGenericMutableModelTree.WriteTransaction<NodeId> {
        return this
    }

    override fun addListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        throw UnsupportedOperationException()
    }

    override fun mutate(parameters: MutationParameters<NodeId>) {
        tree = tree.mutate(parameters).getBlocking(tree)
    }
}

fun <T> IGenericModelTree<T>.asMutableSingleThreaded(): IGenericMutableModelTree<T> = SingleThreadMutableModelTree(this)
fun IGenericModelTree<INodeReference>.asModelSingleThreaded(): IMutableModel = asMutableSingleThreaded().asModel()
fun IGenericModelTree<INodeReference>.asReadOnlyModel(): IModel = asModelSingleThreaded()
