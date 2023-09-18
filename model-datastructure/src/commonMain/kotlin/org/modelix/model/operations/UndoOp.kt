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

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.IKVValue

class UndoOp(val versionHash: KVEntryReference<CPVersion>) : AbstractOperation() {
    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(versionHash)

    override fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation {
        return Applied(
            captureIntend(transaction.tree, store)
                .restoreIntend(transaction.tree)
                .map { it.apply(transaction, store) },
        )
    }

    override fun captureIntend(tree: ITree, store_: IDeserializingKeyValueStore): IOperationIntend {
        val store = (tree as CLTree).store
        val versionToUndo = CLVersion(versionHash.getValue(store), store)
        val originalAppliedOps = getAppliedOps(versionToUndo, store)
        val invertedOps = originalAppliedOps.reversed().flatMap { it.invert() }
        val invertedOpIntends = captureIntend(versionToUndo.tree, invertedOps, store)
        return Intend(invertedOpIntends, store)
    }

    private fun getAppliedOps(version: CLVersion, store: IDeserializingKeyValueStore): List<IAppliedOperation> {
        val tree = version.baseVersion!!.tree
        val branch = TreePointer(tree)
        return branch.computeWrite {
            version.operations.map { it.apply(branch.writeTransaction, store) }
        }
    }

    private fun captureIntend(tree: ITree, ops: List<IOperation>, store: IDeserializingKeyValueStore): List<IOperationIntend> {
        val branch = TreePointer(tree)
        return branch.computeWrite {
            ops.map {
                val intend = it.captureIntend(branch.transaction.tree, store)
                it.apply(branch.writeTransaction, store)
                intend
            }
        }
    }

    private fun restoreIntend(tree: ITree, opIntends: List<IOperationIntend>, store: IDeserializingKeyValueStore): List<IOperation> {
        val branch = TreePointer(tree)
        return branch.computeWrite {
            opIntends.flatMap {
                val restoredOps = it.restoreIntend(branch.transaction.tree)
                restoredOps.forEach { restoredOp -> restoredOp.apply(branch.writeTransaction, store) }
                restoredOps
            }
        }
    }

    override fun toString(): String {
        return "UndoOp ${versionHash.getHash()}"
    }

    inner class Intend(val intends: List<IOperationIntend>, val store: IDeserializingKeyValueStore) : IOperationIntend {
        override fun getOriginalOp(): IOperation {
            return this@UndoOp
        }

        override fun restoreIntend(tree: ITree): List<IOperation> {
            return restoreIntend(tree, intends, store)
        }
    }

    inner class Applied(val appliedOps: List<IAppliedOperation>) : IAppliedOperation {
        override fun getOriginalOp(): IOperation {
            return this@UndoOp
        }

        override fun invert(): List<IOperation> {
            return appliedOps.reversed().flatMap { it.invert() }
        }
    }
}
