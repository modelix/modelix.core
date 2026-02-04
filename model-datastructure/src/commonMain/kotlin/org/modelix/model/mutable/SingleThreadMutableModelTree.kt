package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.TreeId
import org.modelix.model.api.IModel
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.streams.getBlocking

class SingleThreadMutableModelTree<NodeId>(
    override var tree: IGenericModelTree<NodeId>,
    private val idGenerator: INodeIdGenerator<NodeId> = DummyIdGenerator(),
) : IGenericMutableModelTree<NodeId>, IGenericMutableModelTree.WriteTransaction<NodeId> {

    private var listeners: Set<IGenericMutableModelTree.Listener<NodeId>> = emptySet()

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
        listeners += listener
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<NodeId>) {
        listeners -= listener
    }

    override fun mutate(parameters: MutationParameters<NodeId>) {
        val oldTree = tree
        val newTree = tree.mutate(parameters).getBlocking(tree)
        tree = newTree
        for (listener in listeners) {
            listener.treeChanged(oldTree, newTree)
        }
    }
}

/**
 * Allows access to the model without starting a transactions. In unit tests consider using [asMutableThreadSafe] to
 * also test the correct usage of transactions.
 */
@DelicateModelixApi
fun <T> IGenericModelTree<T>.asMutableSingleThreaded(): IGenericMutableModelTree<T> = SingleThreadMutableModelTree(this)

/**
 * Allows access to the model without starting a transactions. In unit tests consider using [asMutableThreadSafe] to
 * also test the correct usage of transactions.
 */
@DelicateModelixApi
fun IGenericModelTree<INodeReference>.asModelSingleThreaded(): IMutableModel = asMutableSingleThreaded().asModel()

fun IGenericModelTree<INodeReference>.asReadOnlyModel(): IModel {
    // OptIn is valid because read-only models aren't supposed to require transactions.
    @OptIn(DelicateModelixApi::class)
    return asModelSingleThreaded()
}
