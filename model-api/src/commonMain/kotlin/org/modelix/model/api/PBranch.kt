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

package org.modelix.model.api

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.area.PArea
import kotlin.jvm.Volatile

class PBranch constructor(@field:Volatile private var tree: ITree, private val idGenerator: IIdGenerator) : IBranch {
    private val logger = mu.KotlinLogging.logger {}
    private val writeLock = Any()
    private val contextTransactions = ContextValue<Transaction?>()
    private var listeners: Set<IBranchListener> = emptySet()
    private val branchId: String = tree.getId() ?: idGenerator.generate().toString()

    override fun getId(): String {
        return branchId
    }

    private fun <T> runWithTransaction(transaction: ITransaction, runnable: () -> T): T {
        return RoleAccessContext.runWith(transaction.tree.usesRoleIds()) {
            INodeResolutionScope.ensureInContext(PArea(this)) {
                contextTransactions.computeWith(transaction as Transaction, runnable)
            }
        }
    }

    override fun runRead(runnable: () -> Unit) {
        val prevTransaction = contextTransactions.getValueOrNull()
        if (prevTransaction is IReadTransaction) {
            runnable()
        } else {
            val currentTree = prevTransaction?.tree ?: tree
            val t = ReadTransaction(currentTree, this)
            runWithTransaction(t, runnable)
        }
    }

    override fun runWrite(runnable: () -> Unit) {
        runSynchronized(writeLock) {
            val prevTransaction = contextTransactions.getValueOrNull()
            check(prevTransaction !is ReadTransaction) { "Cannot run write from read" }
            val prevWrite = prevTransaction as WriteTransaction?
            val oldTree: ITree = prevWrite?.tree ?: tree
            val newWrite = WriteTransaction(oldTree, this, idGenerator)
            try {
                runWithTransaction(newWrite, runnable)
                newWrite.close()
                val newTree: ITree = newWrite.tree
                if (prevWrite == null) {
                    tree = newTree
                    notifyTreeChange(oldTree, newTree)
                } else {
                    prevWrite.tree = newTree
                }
            } finally {
                newWrite.close()
            }
        }
    }

    override fun <T> computeRead(computable: () -> T): T {
        var result: T? = null
        runRead { result = computable() }
        return result as T
    }

    override fun <T> computeWrite(computable: () -> T): T {
        var result: T? = null
        runWrite { result = computable() }
        return result as T
    }

    override fun canRead(): Boolean {
        return contextTransactions.getValueOrNull() != null
    }

    override fun canWrite(): Boolean {
        return contextTransactions.getValueOrNull() is IWriteTransaction
    }

    override val transaction: ITransaction
        get() = contextTransactions.getValueOrNull() ?: throw IllegalStateException("Not in a transaction")

    override val readTransaction: IReadTransaction
        get() {
            val transaction = transaction
            check(transaction is ReadTransaction) { "Not in a read transaction" }
            return transaction
        }

    override val writeTransaction: IWriteTransaction
        get() {
            val transaction = transaction
            check(transaction is WriteTransaction) { "Not in a write transaction" }
            return transaction
        }

    override fun addListener(l: IBranchListener) {
        listeners = listeners + l
    }

    override fun removeListener(l: IBranchListener) {
        listeners = listeners - l
    }

    protected fun notifyTreeChange(oldTree: ITree, newTree: ITree) {
        if (oldTree === newTree) {
            return
        }
        for (l in listeners) {
            try {
                l.treeChanged(oldTree, newTree)
            } catch (ex: Exception) {
                logger.error(ex) { "Exception in branch listener" }
            }
        }
    }
}
