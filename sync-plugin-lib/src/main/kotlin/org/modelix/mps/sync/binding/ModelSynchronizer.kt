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

import jetbrains.mps.project.ModelImporter
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.ICloudRepository
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer

class ModelSynchronizer(
    private val modelNodeId: Long,
    private val model: SModel,
    private val cloudRepository: ICloudRepository,
) {
    companion object {
        val USED_DEVKITS = "usedDevkits"
        val MPS_NODE_ID_PROPERTY_NAME = "#mpsNodeId#"
    }

    private val pendingReferences: PendingReferences = PendingReferences()

    protected val nodeMap = NodeMap { this.getBranch() }

    fun getBranch() = cloudRepository.getBranch()

    fun runAndFlushReferences(runnable: Runnable) = pendingReferences.runAndFlush(runnable)

    fun getOrSyncToCloud(node: SNode, transaction: IWriteTransaction): Long {
        var cloudId = nodeMap.getId(node)
        if (cloudId == 0L || !transaction.containsNode(cloudId)) {
            val parent = node.parent
            if (parent == null) {
                syncRootNodesFromMPS()
            } else {
                val parentCloudId = getOrSyncToCloud(parent, transaction)
                syncNodeFromMPS(parent, true)
            }
            cloudId = nodeMap.getId(node)
        }
        return cloudId
    }

    private fun syncRootNodesFromMPS() {
        val transaction = getBranch().writeTransaction
        // TODO instead of "rootNodes" it must be link/Model: rootNodes/.getName()
        val syncedNodes = createChildrenSynchronizer(modelNodeId, "rootNodes").syncToCloud(transaction)
        syncedNodes.forEach {
            syncNodeFromMPS(it.value, true)
        }
    }

    private fun syncNodeFromMPS(parentNode: SNode, includeDescendants: Boolean) {
        TODO("Not yet implemented")
    }

    private fun createChildrenSynchronizer(parentId: Long, role: String): Synchronizer<SNode> {
        return object : Synchronizer<SNode>(parentId, role) {
            override fun getMPSChildren(): Iterable<SNode> {
                TODO("Not yet implemented")
            }

            override fun createMPSChild(tree: ITree, cloudChildId: Long): SNode? {
                TODO("Not yet implemented")
            }

            override fun associate(
                tree: ITree,
                cloudChildren: List<Long>,
                mpsChildren: List<SNode>,
                direction: SyncDirection,
            ): MutableMap<Long, SNode> {
                TODO("Not yet implemented")
            }

            override fun removeMPSChild(mpsChild: SNode) {
                TODO("Not yet implemented")
            }

            override fun createCloudChild(transaction: IWriteTransaction, mpsChild: SNode): Long {
                TODO("Not yet implemented")
            }
        }
    }

    fun handleReferenceChanged(event: SReferenceChangeEvent) {
        TODO("Not yet implemented")
    }

    fun handleMPSNodeAdded(event: SNodeAddEvent) {
        TODO("Not yet implemented")
    }

    fun handleMPSNodeRemoved(event: SNodeRemoveEvent) {
        TODO("Not yet implemented")
    }

    fun syncUsedLanguagesAndDevKitsFromMPS() {
        TODO("Not yet implemented")
    }

    fun syncModelImportsFromMPS() {
        TODO("Not yet implemented")
    }

    fun fullSyncFromMPS() {
        TODO("Not yet implemented")
    }

    fun syncModelToMPS(tree: ITree, withInitialRemoval: Boolean) {
        TODO("Not yet implemented")
    }

    fun syncChildrenToMPS(nodeId: Long, role: String?, tree: ITree, includeDescendants: Boolean) {
    }

    fun syncReferenceToMPS(nodeId: Long, role: String?, tree: ITree) {
    }

    fun syncPropertyToMPS(nodeId: Long, role: String?, tree: ITree) {
    }

    fun syncNodeToMPS(nodeId: Long, tree: ITree, includeDescendants: Boolean) {
    }

    fun syncModelPropertiesToMPS(tree: ITree) {
    }

    inner class PendingReferences {
        private val logger = mu.KotlinLogging.logger {}

        private var currentReferences: MutableList<() -> SNode>? = null

        fun runAndFlush(runnable: Runnable) {
            synchronized(this) {
                currentReferences?.let {
                    runnable.run()
                    return
                }

                try {
                    currentReferences = mutableListOf()
                    runnable.run()
                } finally {
                    try {
                        processPendingReferences()
                    } catch (ex: Exception) {
                        logger.error(ex) { "Failed to process pending reference" }
                    }
                    currentReferences = null
                }
            }
        }

        private fun processPendingReferences() {
            val targetModels = mutableSetOf<SModel?>()
            currentReferences!!.forEach {
                try {
                    val targetNode = it.invoke()
                    targetModels.add(targetNode.model)
                } catch (ex: Exception) {
                    logger.error(ex) { ex.message }
                }
            }

            val modelsToImport = targetModels.filter { it != null && it != model }
            if (modelsToImport.isNotEmpty()) {
                val importer = ModelImporter(model)
                modelsToImport.forEach { importer.prepare(it?.reference) }
                importer.execute()
            }
        }
    }
}
