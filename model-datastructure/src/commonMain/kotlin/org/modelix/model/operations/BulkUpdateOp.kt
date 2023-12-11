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

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.IKVValue
import org.modelix.model.persistent.SerializationUtil

class BulkUpdateOp(
    val resultTreeHash: KVEntryReference<CPTree>,
    val subtreeRootId: Long,
) : AbstractOperation() {

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(resultTreeHash)

    /**
     * Since this operation is recorded at the end of a bulk update we need to create an IAppliedOperation without
     * actually applying it again.
     */
    fun afterApply(baseTree: CLTree) = Applied(baseTree)

    override fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation {
        val baseTree = transaction.tree as CLTree
        val resultTree = getResultTree(store)
        TODO("Change the (sub)tree so that it is identical to the resultTree")
        return Applied(baseTree)
    }

    private fun getResultTree(store: IDeserializingKeyValueStore): CLTree = CLTree(resultTreeHash.getValue(store), store)

    override fun toString(): String {
        return "BulkUpdateOp ${resultTreeHash.getHash()}, ${SerializationUtil.longToHex(subtreeRootId)}"
    }

    inner class Applied(val baseTree: CLTree) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@BulkUpdateOp

        override fun invert(): List<IOperation> {
            return listOf(BulkUpdateOp(KVEntryReference(baseTree.data), subtreeRootId))
        }
    }

    override fun captureIntend(tree: ITree, store: IDeserializingKeyValueStore): IOperationIntend {
        return Intend()
    }

    inner class Intend : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            // The intended change is to put the model into the given state. Any concurrent change can just be
            // overwritten as long as the subtree root as the starting point still exists.
            return if (tree.containsNode(subtreeRootId)) {
                listOf(getOriginalOp())
            } else {
                listOf(NoOp())
            }
        }

        override fun getOriginalOp() = this@BulkUpdateOp
    }
}
