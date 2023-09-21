/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.operations

import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.IDeserializingKeyValueStore

class OTBranch(
    private val branch: IBranch,
    private val idGenerator: IIdGenerator,
    private val store: IDeserializingKeyValueStore,
) : IBranch {
    private var currentOperations: MutableList<IAppliedOperation> = ArrayList()
    private val completedChanges: MutableList<OpsAndTree> = ArrayList()
    private val id: String = branch.getId()

    fun operationApplied(op: IAppliedOperation) {
        check(canWrite()) { "Only allowed inside a write transaction" }
        currentOperations.add(op)
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
        return if (canWrite()) {
            branch.computeWrite(computable)
        } else {
            branch.computeWriteT { t ->
                try {
                    val result = computable()
                    runSynchronized(completedChanges) {
                        completedChanges += OpsAndTree(currentOperations, t.tree)
                    }
                    result
                } finally {
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
        return OTWriteTransaction(t, this, idGenerator, store)
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
