/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.binding

import org.jetbrains.mps.openapi.model.SModel
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection

class ModelBinding(val modelNodeId: Long, val model: SModel, initialSyncDirection: SyncDirection) :
    BaseBinding(initialSyncDirection) {

    override fun doActivate() {
        TODO("Not yet implemented")
    }

    override fun doDeactivate() {
        TODO("Not yet implemented")
    }

    override fun doSyncToCloud(transaction: IWriteTransaction) {
        TODO("Not yet implemented")
    }

    override fun doSyncToMPS(tree: ITree) {
        TODO("Not yet implemented")
    }

    override fun deactivate() {
        TODO("Not yet implemented")
    }
}
