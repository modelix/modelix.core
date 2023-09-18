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

import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.SerializationUtil

class SetReferenceOp(val sourceId: Long, val role: String, val target: INodeReference?) : AbstractOperation(), IOperationIntend {
    override fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation {
        val oldValue = transaction.getReferenceTarget(sourceId, role)
        transaction.setReferenceTarget(sourceId, role, target)
        return Applied(oldValue)
    }

    override fun toString(): String {
        return "SetReferenceOp ${SerializationUtil.longToHex(sourceId)}.$role = $target"
    }

    override fun restoreIntend(tree: ITree): List<IOperation> {
        return if (tree.containsNode(sourceId)) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: ITree, store: IDeserializingKeyValueStore) = this

    override fun getOriginalOp() = this

    fun withTarget(newTarget: INodeReference?): SetReferenceOp {
        return if (newTarget == target) this else SetReferenceOp(sourceId, role, newTarget)
    }

    inner class Applied(private val oldValue: INodeReference?) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@SetReferenceOp

        override fun invert(): List<IOperation> {
            return listOf(SetReferenceOp(sourceId, role, oldValue))
        }

        override fun toString(): String {
            return super.toString() + ", oldValue: " + oldValue
        }
    }
}
