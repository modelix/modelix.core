/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private var operations: MutableList<IAppliedOperation> = ArrayList()
    private var treeForOperations: ITree = branch.computeRead { branch.transaction.tree }
    private val operationsLock = Any()
    private val id: String = branch.getId()

    fun operationApplied(op: IAppliedOperation) {
        runSynchronized(operationsLock) {
            operations.add(op)
            treeForOperations = transaction.tree
        }
    }

    override fun getId(): String {
        return id
    }

    val operationsAndTree: Pair<List<IAppliedOperation>, ITree>
        get() {
            runSynchronized(operationsLock) {
                val newOperations: List<IAppliedOperation> = operations
                operations = ArrayList()
                return Pair(newOperations, treeForOperations)
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
        return branch.computeWrite(computable)
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
        checkNotEDT()
        branch.runWrite(runnable)
    }

    fun wrap(t: ITransaction): ITransaction {
        return if (t is IWriteTransaction) wrap(t) else t
    }

    fun wrap(t: IWriteTransaction): IWriteTransaction {
        return OTWriteTransaction(t, this, idGenerator, store)
    }

    protected fun checkNotEDT() {}
}
