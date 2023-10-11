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

package org.modelix.mps.sync.tools

import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.util.ProgressMonitor
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.ProjectModuleBinding
import org.modelix.mps.sync.configuration.PersistedBindingConfiguration
import org.modelix.mps.sync.exportFromCloud.ModuleCheckout
import org.modelix.mps.sync.importToCloud.ModelCloudImportUtils
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.transient.TransientModuleBinding
import org.modelix.mps.sync.util.nodeIdAsLong

// status: migrated, but needs some bugfixes
/**
 * This class is responsible for importing local MPS modules into the Modelix server
 */
object ModelCloudImportUtils {

    fun checkoutAndSync(treeNode: CloudNodeTreeNode, mpsProject: Project) {
        val treeInRepository = treeNode.getTreeInRepository()
        val cloudModuleNode = treeNode.node as PNodeAdapter
        val solution = ModuleCheckout(mpsProject, treeInRepository).checkoutCloudModule(cloudModuleNode)
        mpsProject.repository.modelAccess.runReadAction {
            syncInModelixAsIndependentModule(
                treeInRepository,
                solution,
                ProjectHelper.toIdeaProject(mpsProject),
                cloudModuleNode,
            )
        }
        PersistedBindingConfiguration.getInstance(ProjectHelper.toIdeaProject(mpsProject))
            .addMappedBoundModule(treeInRepository, cloudModuleNode)
    }

    fun checkoutAndSync(treeInRepository: CloudRepository, mpsProject: Project, cloudModuleNodeId: Long) {
        val cloudModuleNode = PNodeAdapter(cloudModuleNodeId, treeInRepository.getActiveBranch().branch)
        val solution = ModuleCheckout(mpsProject, treeInRepository).checkoutCloudModule(cloudModuleNode)
        mpsProject.repository.modelAccess.runReadAction {
            syncInModelixAsIndependentModule(
                treeInRepository,
                solution,
                ProjectHelper.toIdeaProject(mpsProject),
                cloudModuleNode,
            )
        }
        PersistedBindingConfiguration.getInstance(ProjectHelper.toIdeaProject(mpsProject))
            .addMappedBoundModule(treeInRepository, cloudModuleNode)
    }

    fun addTransientModuleBinding(
        mpsProject: com.intellij.openapi.project.Project,
        repositoryInModelServer: CloudRepository,
        cloudNodeId: Long,
    ): TransientModuleBinding {
        val modelServerConnection = repositoryInModelServer.modelServer
        val repositoryId = repositoryInModelServer.getRepositoryId()
        val transientModuleBinding = TransientModuleBinding(cloudNodeId)
        modelServerConnection.addBinding(repositoryId, transientModuleBinding)
        PersistedBindingConfiguration.getInstance(mpsProject).addTransientBoundModule(
            repositoryInModelServer,
            repositoryInModelServer.getActiveBranch().branch,
            cloudNodeId,
        )
        return transientModuleBinding
    }

    fun copyAndSyncInModelixAsEntireProject(
        treeInRepository: CloudRepository,
        mpsProject: MPSProject?,
        cloudProject: INode?,
    ): ProjectBinding {
        val binding: ProjectBinding
        if (cloudProject == null) {
            binding = treeInRepository.addProjectBinding(0L, mpsProject!!, SyncDirection.TO_CLOUD)
            PersistedBindingConfiguration.getInstance(ProjectHelper.toIdeaProject(mpsProject))
                .addTransientBoundProject(treeInRepository)
        } else {
            // TODO How to translate this correctly?
            /*
            val cloudProjectAsNodeToSNodeAdapter: NodeToSNodeAdapter = cloudProject as Any as NodeToSNodeAdapter
            val cloudProjectAsINode: INode = cloudProjectAsNodeToSNodeAdapter.getWrapped()
             */
            val cloudProjectAsINode = cloudProject

            val nodeId: Long = cloudProjectAsINode.nodeIdAsLong()
            binding = treeInRepository.addProjectBinding(nodeId, mpsProject!!, SyncDirection.TO_MPS)
            PersistedBindingConfiguration.getInstance(ProjectHelper.toIdeaProject(mpsProject))
                .addTransientBoundProject(treeInRepository)
        }
        return binding
    }

    fun syncInModelixAsIndependentModule(
        treeInRepository: CloudRepository,
        module: SModule,
        mpsProject: com.intellij.openapi.project.Project,
        cloudModuleNode: INode,
    ) {
        val msc = treeInRepository.modelServer
        msc.addBinding(
            treeInRepository.getRepositoryId(),
            ProjectModuleBinding((cloudModuleNode as PNodeAdapter).nodeId, module, SyncDirection.TO_MPS),
        )
        PersistedBindingConfiguration.getInstance(mpsProject).addMappedBoundModule(treeInRepository, cloudModuleNode)
    }

    fun copyAndSyncInModelixAsIndependentModule(
        treeInRepository: CloudRepository,
        module: SModule,
        mpsProject: com.intellij.openapi.project.Project,
        progress: ProgressMonitor,
    ) {
        // 1. Copy the module in the cloud repo
        val cloudModuleNode = ModelCloudImportUtils.copyInModelixAsIndependentModule(treeInRepository, module, progress)
        syncInModelixAsIndependentModule(treeInRepository, module, mpsProject, cloudModuleNode)
    }
}
