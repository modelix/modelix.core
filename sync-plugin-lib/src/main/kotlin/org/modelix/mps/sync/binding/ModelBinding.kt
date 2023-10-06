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

import gnu.trove.set.hash.TLongHashSet
import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.area.PArea
import org.modelix.model.operations.RoleInNode
import org.modelix.mps.sync.synchronization.SyncDirection

// status: ready to test
class ModelBinding(val modelNodeId: Long, private val model: SModel, initialSyncDirection: SyncDirection) :
    Binding(initialSyncDirection) {

    private val logger = mu.KotlinLogging.logger {}

    private val childrenSyncToMPSRequired: MutableSet<RoleInNode> = mutableSetOf()
    private val referenceSyncToMPSRequired: MutableSet<RoleInNode> = mutableSetOf()
    private val propertySyncToMPSRequired: MutableSet<RoleInNode> = mutableSetOf()
    private val fullNodeSyncToMPSRequired: TLongHashSet = TLongHashSet()
    private var modelPropertiesSyncToMPSRequired = false
    private var synchronizer: ModelSynchronizer? = null

    private val nodeChangeListener = object : SNodeChangeListener {
        private val logger = mu.KotlinLogging.logger {}

        override fun propertyChanged(event: SPropertyChangeEvent) {
            try {
                if (isSynchronizing()) {
                    return
                }

                getBranch()?.let {
                    PArea(it).executeWrite {
                        synchronizer?.runAndFlushReferences {
                            val transaction = it.writeTransaction
                            val id = synchronizer?.getOrSyncToCloud(event.node, transaction)
                            if (id != null && id != 0L && transaction.containsNode(id)) {
                                transaction.setProperty(id, event.property.name, event.newValue)
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun referenceChanged(event: SReferenceChangeEvent) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.handleReferenceChanged(event)
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun nodeAdded(event: SNodeAddEvent) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.handleMPSNodeAdded(event)
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun nodeRemoved(event: SNodeRemoveEvent) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.handleMPSNodeRemoved(event)
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }
    }

    private val modelListener = object : SModelListener {
        private val logger = mu.KotlinLogging.logger {}

        override fun languageAdded(event: SModelLanguageEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncUsedLanguagesAndDevKitsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun languageRemoved(event: SModelLanguageEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncUsedLanguagesAndDevKitsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun devkitAdded(event: SModelDevKitEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncUsedLanguagesAndDevKitsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun devkitRemoved(event: SModelDevKitEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncUsedLanguagesAndDevKitsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun beforeChildRemoved(event: SModelChildEvent?) {}
        override fun beforeModelDisposed(sm: SModel?) {}
        override fun beforeModelRenamed(event: SModelRenamedEvent?) {}
        override fun beforeRootRemoved(event: SModelRootEvent?) {}
        override fun childAdded(event: SModelChildEvent?) {}
        override fun childRemoved(event: SModelChildEvent?) {}

        override fun getPriority(): SModelListener.SModelListenerPriority = SModelListener.SModelListenerPriority.CLIENT

        override fun importAdded(event: SModelImportEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncModelImportsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun importRemoved(event: SModelImportEvent?) {
            try {
                if (isSynchronizing()) {
                    return
                }
                synchronizer?.runAndFlushReferences {
                    synchronizer?.syncModelImportsFromMPS()
                }
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun modelLoadingStateChanged(sm: SModel?, newState: ModelLoadingState?) {}
        override fun modelRenamed(event: SModelRenamedEvent?) {}
        override fun modelSaved(sm: SModel?) {}
        override fun propertyChanged(event: SModelPropertyEvent?) {}
        override fun referenceAdded(event: SModelReferenceEvent?) {}
        override fun referenceRemoved(event: SModelReferenceEvent?) {}

        @Deprecated("Deprecated in Java")
        override fun rootAdded(event: SModelRootEvent?) {
        }

        @Deprecated("Deprecated in Java")
        override fun rootRemoved(event: SModelRootEvent?) {
        }
    }

    override fun doActivate() {
        synchronizer = ModelSynchronizer(modelNodeId, model, getCloudRepository()!!)
        model.addChangeListener(nodeChangeListener)
        (model as SModelInternal).addModelListener(modelListener)
    }

    override fun doDeactivate() {
        model.removeChangeListener(nodeChangeListener)
        (model as SModelInternal).removeModelListener(modelListener)
        synchronizer = null
    }

    override fun doSyncToCloud(transaction: IWriteTransaction) {
        synchronizer?.fullSyncFromMPS()
    }

    override fun doSyncToMPS(tree: ITree) {
        if (runningTask?.isInitialSync == true) {
            val mpsRootNodes = model.rootNodes
            val cloudRootNodes =
                tree.getChildren(modelNodeId, BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName())
            val mpsRootNodesIsNotEmpty = mpsRootNodes.firstOrNull() != null
            val cloudRootNodesIsEmpty = cloudRootNodes.firstOrNull() == null
            if (mpsRootNodesIsNotEmpty && cloudRootNodesIsEmpty) {
                // TODO remove this workaround
                forceEnqueueSyncTo(SyncDirection.TO_CLOUD, true, null)
            } else {
                synchronizer?.syncModelToMPS(tree, false)
            }
        } else {
            synchronizer?.runAndFlushReferences {
                childrenSyncToMPSRequired.forEach { roleInNode ->
                    try {
                        if (tree.containsNode(roleInNode.nodeId)) {
                            synchronizer?.syncChildrenToMPS(roleInNode.nodeId, roleInNode.role!!, tree, false)
                        }
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                }
                childrenSyncToMPSRequired.clear()

                referenceSyncToMPSRequired.forEach { roleInNode ->
                    try {
                        if (tree.containsNode(roleInNode.nodeId)) {
                            synchronizer?.syncReferenceToMPS(roleInNode.nodeId, roleInNode.role!!, tree)
                        }
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                }
                referenceSyncToMPSRequired.clear()

                propertySyncToMPSRequired.forEach { roleInNode ->
                    try {
                        if (tree.containsNode(roleInNode.nodeId)) {
                            synchronizer?.syncPropertyToMPS(roleInNode.nodeId, roleInNode.role!!, tree)
                        }
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                }
                propertySyncToMPSRequired.clear()

                fullNodeSyncToMPSRequired.forEach { nodeId ->
                    try {
                        if (tree.containsNode(nodeId)) {
                            synchronizer?.syncNodeToMPS(nodeId, tree, true)
                        }
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                    true
                }
                fullNodeSyncToMPSRequired.clear()

                if (modelPropertiesSyncToMPSRequired) {
                    try {
                        synchronizer?.syncModelPropertiesToMPS(tree)
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                }
            }
            modelPropertiesSyncToMPSRequired = false
        }
    }

    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree?): ITreeChangeVisitor {
        return object : ITreeChangeVisitorEx {
            private fun isInsideModel(nodeId: Long): Boolean {
                assertSyncThread()
                val parent = newTree?.getParent(nodeId)!!
                if (parent == 0L) {
                    return false
                }
                if (parent == modelNodeId) {
                    return newTree.getRole(nodeId) == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()
                }
                return isInsideModel(parent)
            }

            private fun isInsideModelOrModel(nodeId: Long): Boolean {
                assertSyncThread()
                return if (nodeId == modelNodeId) {
                    true
                } else {
                    isInsideModel(nodeId)
                }
            }

            private fun isModelProperties(nodeId: Long): Boolean {
                assertSyncThread()
                val parent = newTree?.getParent(nodeId)!!
                if (parent == 0L) {
                    return false
                }
                if (parent == modelNodeId) {
                    return newTree.getRole(nodeId) != BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()
                }
                return isModelProperties(parent)
            }

            override fun containmentChanged(nodeId: Long) {}

            override fun childrenChanged(nodeId: Long, role: String?) {
                assertSyncThread()
                if (modelNodeId == nodeId) {
                    if (role == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()) {
                        childrenSyncToMPSRequired.add(RoleInNode(nodeId, role))
                    } else {
                        modelPropertiesSyncToMPSRequired = true
                    }
                } else if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                } else if (isInsideModel(nodeId)) {
                    childrenSyncToMPSRequired.add(RoleInNode(nodeId, role))
                }
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun referenceChanged(nodeId: Long, role: String) {
                assertSyncThread()
                if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                    enqueueSync(SyncDirection.TO_MPS, false, null)
                    return
                }
                if (!isInsideModel(nodeId)) {
                    return
                }
                referenceSyncToMPSRequired.add(RoleInNode(nodeId, role))
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun propertyChanged(nodeId: Long, role: String) {
                assertSyncThread()
                if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                    enqueueSync(SyncDirection.TO_MPS, false, null)
                    return
                }
                if (!isInsideModel(nodeId)) {
                    return
                }
                propertySyncToMPSRequired.add(RoleInNode(nodeId, role))
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun nodeRemoved(nodeId: Long) {}

            override fun nodeAdded(nodeId: Long) {
                assertSyncThread()
                if (!isInsideModel(nodeId)) {
                    return
                }
                fullNodeSyncToMPSRequired.add(nodeId)
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }
        }
    }

    override fun toString(): String = "Model: ${java.lang.Long.toHexString(modelNodeId)} -> ${model.name.value}"
}
