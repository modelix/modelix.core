package org.modelix.model.mutable

import kotlinx.datetime.Clock
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.api.INodeReference
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.BulkUpdateOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.PositionInRole
import org.modelix.model.operations.SetConceptOp
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp

class VersionedModelTree(
    initialVersion: IVersion,
    nodeIdGenerator: INodeIdGenerator<INodeReference> = DummyIdGenerator(),
) : IMutableModelTree {

    private var baseVersion: IVersion = initialVersion
    private val mutableTree = ThreadSafeMutableModelTree(baseVersion.getModelTree(), nodeIdGenerator)
    private var bulkUpdateMode: Boolean = false
    private var currentOperations: MutableList<IAppliedOperation> = ArrayList()
    private val completedChanges: MutableList<OpsAndTree> = ArrayList()
    private var inWriteTransaction = false

    override fun getId(): TreeId {
        return mutableTree.getId()
    }

    override fun getIdGenerator(): INodeIdGenerator<INodeReference> {
        return mutableTree.getIdGenerator()
    }

    override fun <R> runRead(body: (IGenericMutableModelTree.Transaction<INodeReference>) -> R): R {
        return mutableTree.runRead(body)
    }

    override fun <R> runWrite(body: (IGenericMutableModelTree.WriteTransaction<INodeReference>) -> R): R {
        return runSynchronized(this) {
            mutableTree.runWrite { t ->
                // canWrite() cannot be used as the condition, because that may statically return true (see TreePointer)
                if (inWriteTransaction) {
                    body(VersionedWriteTransaction(t))
                } else {
                    try {
                        inWriteTransaction = true
                        val result = body(VersionedWriteTransaction(t))
                        runSynchronized(completedChanges) {
                            completedChanges += OpsAndTree(currentOperations, t.tree)
                        }
                        result
                    } finally {
                        inWriteTransaction = false
                        currentOperations = ArrayList()
                    }
                }
            }
        }
    }

    override fun canRead(): Boolean {
        return mutableTree.canRead()
    }

    override fun canWrite(): Boolean {
        return mutableTree.canWrite()
    }

    override fun getTransaction(): IGenericMutableModelTree.Transaction<INodeReference> {
        return mutableTree.getTransaction().let {
            if (it is IGenericMutableModelTree.WriteTransaction) VersionedWriteTransaction(it) else it
        }
    }

    override fun getWriteTransaction(): IGenericMutableModelTree.WriteTransaction<INodeReference> {
        return VersionedWriteTransaction(mutableTree.getWriteTransaction())
    }

    override fun addListener(listener: IGenericMutableModelTree.Listener<INodeReference>) {
        mutableTree.addListener(listener)
    }

    override fun removeListener(listener: IGenericMutableModelTree.Listener<INodeReference>) {
        mutableTree.removeListener(listener)
    }

    /**
     * This records all changes as a single operation instead of a long list of fine-grained changes.
     * It is assumed that the intended change is to put the model into the resulting state.
     *
     * @param subtreeRootNodeId if the update is not applied on the whole model, but only on a part of it,
     *                          this ID specifies the root node of that subtree.
     */
    fun runBulkUpdate(subtreeRootNodeId: INodeReference, body: () -> Unit) {
        check(canWrite()) { "Only allowed inside a write transaction" }
        if (bulkUpdateMode) return body()
        try {
            bulkUpdateMode = true
            val baseTree = mutableTree.getTransaction().tree
            body()
            val resultTree = mutableTree.getTransaction().tree
            currentOperations += BulkUpdateOp(resultTree.asObject().ref, subtreeRootNodeId).afterApply(baseTree)
        } finally {
            bulkUpdateMode = false
        }
    }

    fun isInBulkMode() = bulkUpdateMode

    fun apply(op: IOperation) {
        check(canWrite()) { "Only allowed inside a write transaction" }
        val appliedOp = op.apply(this@VersionedModelTree.mutableTree)
        if (!bulkUpdateMode) {
            currentOperations.add(appliedOp)
        }
    }

    /**
     * @return the operations applied to the tree since the last call of this function.
     */
    private fun getPendingChanges(): Pair<List<IAppliedOperation>, IModelTree> {
        return runSynchronized(completedChanges) {
            val result = when (completedChanges.size) {
                0 -> OpsAndTree(emptyList(), runRead { it.tree })
                1 -> completedChanges.single()
                else -> completedChanges.last().merge(completedChanges.dropLast(1))
            }.asPair()
            completedChanges.clear()
            result
        }
    }

    /**
     * @return null if there are no changes
     */
    fun createVersion(versionId: Long, author: String?): IVersion? {
        runSynchronized(completedChanges) {
            val (ops, newTree) = getPendingChanges()
            val oldTreeHash = baseVersion.getModelTree().asObject().getHash()
            val newTreeHash = newTree.asObject().getHash()
            if (oldTreeHash == newTreeHash && ops.isEmpty()) return null
            val newVersion = CLVersion.builder()
                .id(versionId)
                .tree(newTree)
                .regularUpdate(baseVersion)
                .operations(ops.map { it.getOriginalOp() })
                .author(author)
                .time(Clock.System.now())
                .build()
            this.baseVersion = newVersion
            return newVersion
        }
    }

    inner class VersionedWriteTransaction(val t: IGenericMutableModelTree.WriteTransaction<INodeReference>) : IGenericMutableModelTree.WriteTransaction<INodeReference> {
        override var tree: IGenericModelTree<INodeReference>
            get() = t.tree
            set(value) {
                throw UnsupportedOperationException()
            }

        override fun mutate(parameters: MutationParameters<INodeReference>) {
            when (parameters) {
                is MutationParameters.AddNew<INodeReference> -> {
                    apply(
                        AddNewChildrenOp(
                            position = PositionInRole(parameters.nodeId, parameters.role, parameters.index),
                            childIdsAndConcepts = parameters.newIdAndConcept.toList(),
                        ),
                    )
                }
                is MutationParameters.Move<INodeReference> -> {
                    val targetPosition = PositionInRole(parameters.nodeId, parameters.role, parameters.index)
                    for (childId in parameters.existingChildIds.reversed()) {
                        apply(MoveNodeOp(childId = childId, targetPosition = targetPosition))
                    }
                }
                is MutationParameters.Concept<INodeReference> -> {
                    apply(SetConceptOp(parameters.nodeId, parameters.concept))
                }
                is MutationParameters.Property<INodeReference> -> {
                    apply(SetPropertyOp(parameters.nodeId, parameters.role, parameters.value))
                }
                is MutationParameters.Reference<INodeReference> -> {
                    apply(SetReferenceOp(parameters.nodeId, parameters.role, parameters.target))
                }
                is MutationParameters.Remove<INodeReference> -> {
                    apply(DeleteNodeOp(parameters.nodeId))
                }
            }
        }
    }

    private class OpsAndTree(val ops: List<IAppliedOperation>, val tree: IModelTree) {
        fun asPair(): Pair<List<IAppliedOperation>, IModelTree> = ops to tree
        fun merge(previous: List<OpsAndTree>): OpsAndTree {
            return OpsAndTree((previous + this).flatMap { it.ops }.toList(), tree)
        }
    }
}
