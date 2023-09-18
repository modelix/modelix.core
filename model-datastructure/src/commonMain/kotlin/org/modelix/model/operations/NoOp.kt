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
import org.modelix.model.lazy.IDeserializingKeyValueStore

class NoOp : AbstractOperation(), IAppliedOperation, IOperationIntend {
    override fun apply(transaction: IWriteTransaction, store: IDeserializingKeyValueStore): IAppliedOperation {
        return this
    }

    override fun invert(): List<IOperation> {
        return listOf(this)
    }

    override fun toString(): String {
        return "NoOp"
    }

    override fun captureIntend(tree: ITree, store: IDeserializingKeyValueStore) = this

    override fun getOriginalOp() = this

    override fun restoreIntend(tree: ITree) = listOf(this)
}
