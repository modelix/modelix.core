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

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepositoryListenerBase
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection

// status: ready to test
class ProjectBinding(val mpsProject: MPSProject, projectNodeId: Long, initialSyncDirection: SyncDirection) :
    Binding(initialSyncDirection) {

    private val logger = logger<ProjectBinding>()
    private val repositoryListener = RepositoryListener()
    var projectNodeId: Long = projectNodeId
        private set

    init {
        logger.debug("Project binding created: $this")
    }

    override fun doActivate() {
        logger.debug("Activating: $this")
        val branch = getBranch() ?: return

        if (projectNodeId == 0L) {
            mpsProject.repository.modelAccess.runReadAction {
                branch.runWriteT {
                    projectNodeId = it.addNewChild(
                        ITree.ROOT_ID,
                        BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.getSimpleName(),
                        -1,
                        BuiltinLanguages.MPSRepositoryConcepts.Project,
                    )

                    it.setProperty(
                        projectNodeId,
                        BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName(),
                        mpsProject.name,
                    )
                }
                enqueueSync(SyncDirection.TO_CLOUD, true, null)
            }
        } else {
            val cloudProjectIsEmpty = branch.computeReadT {
                val children = it.getChildren(
                    projectNodeId,
                    BuiltinLanguages.MPSRepositoryConcepts.Project.modules.getSimpleName(),
                )
                !children.any()
            }
            if (cloudProjectIsEmpty) {
                enqueueSync(SyncDirection.TO_CLOUD, true, null)
            } else {
                enqueueSync(SyncDirection.TO_MPS, true, null)
            }
        }
        mpsProject.repository.addRepositoryListener(repositoryListener)
        logger.debug("Activated: $this")
    }

    override fun doDeactivate() = mpsProject.repository.removeRepositoryListener(repositoryListener)

    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree?): ITreeChangeVisitor {
        assertSyncThread()
        return object : ITreeChangeVisitor {
            override fun containmentChanged(nodeId: Long) {}

            override fun childrenChanged(nodeId: Long, role: String?) {
                assertSyncThread()
                if (nodeId == projectNodeId && role == BuiltinLanguages.MPSRepositoryConcepts.Project.modules.getSimpleName()) {
                    enqueueSync(SyncDirection.TO_MPS, false, null)
                }
            }

            override fun referenceChanged(nodeId: Long, role: String) {}

            override fun propertyChanged(nodeId: Long, role: String) {}
        }
    }

    override fun doSyncToMPS(tree: ITree) {
        val mappings = ProjectModulesSynchronizer(projectNodeId, mpsProject).syncToMPS(tree)
        updateBindings(mappings, SyncDirection.TO_MPS)
    }

    override fun doSyncToCloud(transaction: IWriteTransaction) {
        val mappings = ProjectModulesSynchronizer(projectNodeId, mpsProject).syncToCloud(transaction)
        updateBindings(mappings, SyncDirection.TO_CLOUD)
    }

    private fun updateBindings(mappings: Map<Long, SModule>, syncDirection: SyncDirection) {
        val mappingsWithoutReadonly = mappings.filter { !it.value.isPackaged && !it.value.isReadOnly }

        val bindings = mutableMapOf<Long, ProjectModuleBinding>()
        ownedBindings.filterIsInstance<ProjectModuleBinding>().forEach { bindings[it.moduleNodeId] = it }

        val toAdd = mappingsWithoutReadonly.map { it.key }.minus(bindings.keys)
        val toRemove = bindings.keys.minus(mappingsWithoutReadonly.map { it.key }.toSet())

        toRemove.forEach {
            val binding = bindings[it]!!
            binding.deactivate(null)
            binding.owner = null
        }

        toAdd.forEach {
            val binding = ProjectModuleBinding(it, mappings[it]!!, syncDirection)
            binding.owner = this
            binding.activate(null)
        }
    }

    override fun toString() = "Project: ${java.lang.Long.toHexString(projectNodeId)} -> ${mpsProject.name}"

    inner class RepositoryListener : SRepositoryListenerBase() {
        override fun moduleAdded(p1: SModule) = enqueueSyncToCloud()

        override fun moduleRemoved(p1: SModuleReference) = enqueueSyncToCloud()

        private fun enqueueSyncToCloud() = enqueueSync(SyncDirection.TO_CLOUD, false, null)
    }
}
