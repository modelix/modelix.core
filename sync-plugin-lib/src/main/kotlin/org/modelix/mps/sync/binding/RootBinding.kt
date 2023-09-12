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

import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.ICloudRepository
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.SyncQueue

// status: ready to test
/**
 * Just a parent for all actual bindings
 */
class RootBinding(private val cloudRepository: ICloudRepository) : Binding(null), IBranchListener {

    private var disposed = false
    val syncQueue: SyncQueue = SyncQueue(this)

    init {
        activate()
    }

    constructor(connection: ModelServerConnection, cloudRepositoryId: RepositoryId) : this(
        CloudRepository(
            connection,
            cloudRepositoryId,
        ),
    )

    public override fun getBranch() = super.getBranch() ?: cloudRepository.getBranch()

    override fun getCloudRepository(): ICloudRepository = cloudRepository

    override fun treeChanged(oldTree: ITree?, newTree: ITree) {
        if (!syncQueue.isSynchronizing) {
            enqueueSync(SyncDirection.TO_MPS, false, null)
        }
    }

    override fun doSyncToMPS(tree: ITree) {
        assertSyncThread()
        val oldTree = syncQueue.lastTreeAfterSync
        if (oldTree != null && tree != oldTree) {
            val visitors = getAllBindings().mapNotNull { it.getTreeChangeVisitor(oldTree, tree) }
            if (visitors.isNotEmpty()) {
                tree.visitChanges(oldTree, TreeChangeMulticaster(visitors))
            }
        }
    }

    override fun doSyncToCloud(transaction: IWriteTransaction) {}

    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree?): ITreeChangeVisitor? = null

    override fun doActivate() {
        check(disposed) { "Reactivation not supported" }
        cloudRepository.getActiveBranch().addListener(this)
    }

    override fun doDeactivate() {
        cloudRepository.getActiveBranch().removeListener(this)
        disposed = true
    }

    override fun toString() = "bindings"
}
