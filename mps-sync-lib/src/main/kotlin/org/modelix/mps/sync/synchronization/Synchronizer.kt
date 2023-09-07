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

package org.modelix.mps.sync.synchronization

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction

/**
Synchronizes an unordered list of children
 */
abstract class Synchronizer<MPSChildT>(val cloudParentId: Long, val cloudRole: String) {

    abstract fun getMPSChildren(): Iterable<MPSChildT>
    protected abstract fun createCloudChild(transaction: IWriteTransaction, mpsChild: MPSChildT): Long
    protected abstract fun removeMPSChild(mpsChild: MPSChildT)
    protected abstract fun createMPSChild(tree: ITree, cloudChildId: Long): MPSChildT?

    abstract fun associate(
        tree: ITree,
        cloudChildren: List<Long>,
        mpsChildren: List<MPSChildT>,
        direction: SyncDirection,
    ): MutableMap<Long, MPSChildT>

    fun getCloudChildren(tree: ITree): Iterable<Long> = tree.getChildren(cloudParentId, cloudRole)

    fun syncToCloud(transaction: IWriteTransaction): Map<Long, MPSChildT> {
        val expectedChildren = getMPSChildren().toList()
        val existingChildren = getCloudChildren(transaction.tree).toList()

        val mappings = associate(transaction.tree, existingChildren, expectedChildren, SyncDirection.TO_CLOUD)

        val toAdd = expectedChildren.minus(mappings.values.toSet()).toList()
        val toRemove = existingChildren.minus(mappings.keys).toList()

        toRemove.forEach { transaction.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, it) }
        toAdd.forEach { mappings[createCloudChild(transaction, it)] = it }

        return mappings
    }

    fun syncToMPS(tree: ITree): Map<Long, MPSChildT> {
        val expectedChildren = getCloudChildren(tree).toList()
        val existingChildren = getMPSChildren().toList()

        val mappings = associate(tree, expectedChildren, existingChildren, SyncDirection.TO_MPS)

        val toAdd = expectedChildren.minus(mappings.keys).toList()
        val toRemove = existingChildren.minus(mappings.values.toSet()).toList()

        toRemove.forEach { removeMPSChild(it) }
        toAdd.forEach { id -> createMPSChild(tree, id)?.let { child -> mappings[id] = child } }

        return mappings
    }
}
