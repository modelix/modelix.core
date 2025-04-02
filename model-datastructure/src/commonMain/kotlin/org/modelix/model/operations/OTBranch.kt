package org.modelix.model.operations

import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.runSynchronized
import org.modelix.model.persistent.getTreeObject

class OTBranch(
    private val branch: IBranch,
    private val idGenerator: IIdGenerator,
) : IBranch {
    private var bulkUpdateMode: Boolean = false
    private var currentOperations: MutableList<IAppliedOperation> = ArrayList()
    private val completedChanges: MutableList<OpsAndTree> = ArrayList()
    private val id: String = branch.getId()
    private var inWriteTransaction = false

    /**
     * This records all changes as a single operation instead of a long list of fine-grained changes.
     * It is assumed that the intended change is to put the model into the resulting state.
     *
     * @param subtreeRootNodeId if the update is not applied on the whole model, but only on a part of it,
     *                          this ID specifies the root node of that subtree.
     */
    fun runBulkUpdate(subtreeRootNodeId: Long = ITree.ROOT_ID, body: () -> Unit) {
        check(canWrite()) { "Only allowed inside a write transaction" }
        if (bulkUpdateMode) return body()
        try {
            bulkUpdateMode = true
            val baseTree = branch.transaction.tree
            body()
            val resultTree = branch.transaction.tree
            currentOperations += BulkUpdateOp(resultTree.getTreeObject().ref, subtreeRootNodeId).afterApply(baseTree)
        } finally {
            bulkUpdateMode = false
        }
    }

    fun isInBulkMode() = bulkUpdateMode

    fun operationApplied(op: IAppliedOperation) {
        check(canWrite()) { "Only allowed inside a write transaction" }
        if (!bulkUpdateMode) {
            currentOperations.add(op)
        }
    }

    override fun getId(): String {
        return id
    }

    @Deprecated("renamed to getPendingChanges()", ReplaceWith("getPendingChanges()"))
    val operationsAndTree: Pair<List<IAppliedOperation>, ITree> get() = getPendingChanges()

    /**
     * @return the operations applied to the branch since the last call of this function and the resulting ITree.
     */
    fun getPendingChanges(): Pair<List<IAppliedOperation>, ITree> {
        return runSynchronized(completedChanges) {
            val result = when (completedChanges.size) {
                0 -> OpsAndTree(computeReadT { it.tree })
                1 -> completedChanges.single()
                else -> completedChanges.last().merge(completedChanges.dropLast(1))
            }.asPair()
            completedChanges.clear()
            result
        }
    }

    override fun addListener(l: IBranchListener) {
        branch.addListener(l)
    }

    override fun removeListener(l: IBranchListener) {
        branch.removeListener(l)
    }

    override fun canRead(): Boolean {
        return branch.canRead()
    }

    override fun canWrite(): Boolean {
        return branch.canWrite()
    }

    override fun <T> computeRead(computable: () -> T): T {
        checkNotEDT()
        return branch.computeRead(computable)
    }

    override fun <T> computeWrite(computable: () -> T): T {
        checkNotEDT()
        return branch.computeWriteT { t ->
            // canWrite() cannot be used as the condition, because that may statically return true (see TreePointer)
            if (inWriteTransaction) {
                computable()
            } else {
                try {
                    inWriteTransaction = true
                    val result = computable()
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

    override val readTransaction: IReadTransaction
        get() = branch.readTransaction

    override val transaction: ITransaction
        get() = wrap(branch.transaction)

    override val writeTransaction: IWriteTransaction
        get() = wrap(branch.writeTransaction)

    override fun runRead(runnable: () -> Unit) {
        checkNotEDT()
        branch.runRead(runnable)
    }

    override fun runWrite(runnable: () -> Unit) {
        computeWrite(runnable)
    }

    fun wrap(t: ITransaction): ITransaction {
        return if (t is IWriteTransaction) wrap(t) else t
    }

    fun wrap(t: IWriteTransaction): IWriteTransaction {
        return OTWriteTransaction(t, this, idGenerator)
    }

    protected fun checkNotEDT() {}

    private class OpsAndTree(val ops: List<IAppliedOperation>, val tree: ITree) {
        constructor(tree: ITree) : this(emptyList(), tree)
        fun asPair(): Pair<List<IAppliedOperation>, ITree> = ops to tree
        fun merge(previous: List<OpsAndTree>): OpsAndTree {
            return OpsAndTree((previous + this).flatMap { it.ops }.toList(), tree)
        }
    }
}
