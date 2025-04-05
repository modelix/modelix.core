package org.modelix.model.operations

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.asModelTree
import org.modelix.datastructures.model.extractInt64Id
import org.modelix.datastructures.model.withIdTranslation
import org.modelix.model.TreeId
import org.modelix.model.api.IBranch
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITransaction
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.async.getTreeId
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.INodeIdGenerator

class LegacyBranchAsMutableModelTree(val branch: IBranch) : IGenericMutableModelTree<INodeReference> {

    private fun INodeReference.toLong() = extractInt64Id(getId())

    override fun getId(): TreeId {
        return branch.transaction.tree.getTreeId()
    }

    override fun getIdGenerator(): INodeIdGenerator<INodeReference> {
        throw UnsupportedOperationException()
    }

    override fun <R> runRead(body: (IGenericMutableModelTree.Transaction<INodeReference>) -> R): R {
        return branch.computeReadT { body(ReadTransactionWrapper(it)) }
    }

    override fun <R> runWrite(body: (IGenericMutableModelTree.WriteTransaction<INodeReference>) -> R): R {
        return branch.computeWriteT { body(WriteTransactionWrapper(it)) }
    }

    override fun canRead(): Boolean {
        return branch.canRead()
    }

    override fun canWrite(): Boolean {
        return branch.canWrite()
    }

    override fun getTransaction(): IGenericMutableModelTree.Transaction<INodeReference> {
        val t = branch.transaction
        return when (t) {
            is IWriteTransaction -> WriteTransactionWrapper(t)
            is IReadTransaction -> ReadTransactionWrapper(t)
            else -> error("Unknown transaction type: $t")
        }
    }

    override fun getWriteTransaction(): IGenericMutableModelTree.WriteTransaction<INodeReference> {
        return WriteTransactionWrapper(branch.writeTransaction)
    }

    override fun addListener(listener: IGenericMutableModelTree.Listener<INodeReference>) {
        TODO("Not yet implemented")
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<INodeReference>) {
        TODO("Not yet implemented")
    }

    abstract inner class TransactionWrapper : IGenericMutableModelTree.Transaction<INodeReference> {
        abstract val t: ITransaction

        override val tree: IGenericModelTree<INodeReference>
            get() = t.tree.asModelTree().withIdTranslation()
    }

    inner class ReadTransactionWrapper(override val t: IReadTransaction) : TransactionWrapper()

    inner class WriteTransactionWrapper(override val t: IWriteTransaction) : TransactionWrapper(), IGenericMutableModelTree.WriteTransaction<INodeReference> {
        override var tree: IGenericModelTree<INodeReference>
            get() = t.tree.asModelTree().withIdTranslation()
            set(value) { TODO() }

        override fun mutate(parameters: MutationParameters<INodeReference>) {
            when (parameters) {
                is MutationParameters.AddNew<INodeReference> -> {
                    for ((childId, concept) in parameters.newIdAndConcept.reversed()) {
                        t.addNewChild(
                            parentId = parameters.nodeId.toLong(),
                            role = parameters.role.stringForLegacyApi(),
                            index = parameters.index,
                            childId = childId.toLong(),
                            concept = concept,
                        )
                    }
                }
                is MutationParameters.Move<INodeReference> -> {
                    for (childId in parameters.existingChildIds.reversed()) {
                        t.moveChild(
                            newParentId = parameters.nodeId.toLong(),
                            newRole = parameters.role.stringForLegacyApi(),
                            newIndex = parameters.index,
                            childId = childId.toLong(),
                        )
                    }
                }
                is MutationParameters.Concept<INodeReference> -> {
                    t.setConcept(parameters.nodeId.toLong(), parameters.concept)
                }
                is MutationParameters.Property<INodeReference> -> {
                    t.setProperty(
                        nodeId = parameters.nodeId.toLong(),
                        role = parameters.role.stringForLegacyApi(),
                        value = parameters.value,
                    )
                }
                is MutationParameters.Reference<INodeReference> -> {
                    t.setReferenceTarget(
                        sourceId = parameters.nodeId.toLong(),
                        role = parameters.role.stringForLegacyApi(),
                        target = parameters.target,
                    )
                }
                is MutationParameters.Remove<INodeReference> -> {
                    t.deleteNode(parameters.nodeId.toLong())
                }
            }
        }
    }
}
