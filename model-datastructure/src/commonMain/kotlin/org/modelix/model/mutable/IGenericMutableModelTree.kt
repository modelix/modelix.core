package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.TreeId
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeReference

typealias IMutableModelTree = IGenericMutableModelTree<INodeReference>

interface IGenericMutableModelTree<NodeId> {
    fun getId(): TreeId
    fun getIdGenerator(): INodeIdGenerator<NodeId>
    fun <R> runRead(body: (Transaction<NodeId>) -> R): R
    fun <R> runWrite(body: (WriteTransaction<NodeId>) -> R): R
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun getTransaction(): Transaction<NodeId>
    fun getWriteTransaction(): WriteTransaction<NodeId>
    fun addListener(listener: Listener<NodeId>)
    fun removeListener(listener: Listener<NodeId>)

    interface Transaction<NodeId> {
        val tree: IGenericModelTree<NodeId>
    }

    interface WriteTransaction<NodeId> : Transaction<NodeId> {
        override var tree: IGenericModelTree<NodeId>
        fun mutate(parameters: MutationParameters<NodeId>)
    }

    interface Listener<NodeId> {
        fun treeChanged(oldTree: IGenericModelTree<NodeId>, newTree: IGenericModelTree<NodeId>)
    }
}

interface INodeIdGenerator<NodeId> {
    fun generate(parentNode: NodeId): NodeId
}

class DummyIdGenerator<NodeId>() : INodeIdGenerator<NodeId> {
    override fun generate(parentNode: NodeId): NodeId {
        throw UnsupportedOperationException("Creating nodes with new ID is not supported")
    }
}

class ModelixIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(parentNode: INodeReference): INodeReference {
        return PNodeReference(int64Generator.generate(), treeId.id)
    }
}
