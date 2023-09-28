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

package org.modelix.mps.sync.importToCloud

import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.util.ProgressMonitor
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.ProjectModuleBinding
import org.modelix.mps.sync.exportFromCloud.ModuleCheckout
import org.modelix.mps.sync.history.CloudNodeTreeNode
import org.modelix.mps.sync.plugin.config.PersistedBindingConfiguration
import org.modelix.mps.sync.synchronization.PhysicalToCloudModelMapping
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.transient.TransientModuleBinding
import org.modelix.mps.sync.util.cloneChildren
import org.modelix.mps.sync.util.copyProperty
import org.modelix.mps.sync.util.getModelsWithoutDescriptor
import org.modelix.mps.sync.util.mapToMpsNode
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.function.Consumer

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

    fun bindCloudProjectToMpsProject(
        repositoryInModelServer: CloudRepository,
        cloudProjectId: Long,
        mpsProject: MPSProject,
        initialSyncDirection: SyncDirection,
    ) = repositoryInModelServer.addBinding(ProjectBinding(mpsProject, cloudProjectId, initialSyncDirection))

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

    fun containsModule(treeInRepository: CloudRepository, module: SModule) =
        treeInRepository.hasModuleInRepository(module.moduleId.toString())

    /**
     * We create an exact copy of a physical module into Modelix, as a root level module
     * (i.e., a module right below a Tree)
     */
    fun copyInModelixAsIndependentModule(
        treeInRepository: CloudRepository,
        module: SModule,
        progress: ProgressMonitor,
    ): INode {
        // First create the module
        val cloudModuleNode = treeInRepository.createModule(module.moduleName!!)
        replicatePhysicalModule(treeInRepository, cloudModuleNode, module, null, progress)
        return cloudModuleNode
    }

    fun copyAndSyncInModelixAsIndependentModule(
        treeInRepository: CloudRepository,
        module: SModule,
        mpsProject: com.intellij.openapi.project.Project,
        progress: ProgressMonitor,
    ) {
        // 1. Copy the module in the cloud repo
        val cloudModuleNode = copyInModelixAsIndependentModule(treeInRepository, module, progress)
        syncInModelixAsIndependentModule(treeInRepository, module, mpsProject, cloudModuleNode)
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

    /**
     * Take an INode already created and make sure it is exactly the same as the physical module given.
     * The modelMappingConsumer may be used to attach a model synchronizer, for example. It is optional.
     */
    private fun replicatePhysicalModule(
        treeInRepository: CloudRepository,
        cloudModule: INode,
        physicalModule: SModule,
        modelMappingConsumer: Consumer<PhysicalToCloudModelMapping>?,
        progress: ProgressMonitor?,
    ) {
        val monitor = progress ?: EmptyProgressMonitor()

        // TODO fixme. Problem SModuleAsNode.wrap does not exist anymore in modelix...
        // SModuleAsNode.wrap(physicalModule);
        val sModuleAsNode: INode = null!!

        treeInRepository.runWrite {
            cloudModule.copyProperty(sModuleAsNode, BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            cloudModule.copyProperty(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.id)
            cloudModule.copyProperty(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion)
            cloudModule.copyProperty(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS)

            cloudModule.cloneChildren(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.facets)
            cloudModule.cloneChildren(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
            cloudModule.cloneChildren(sModuleAsNode, BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies)
        }

        var models = listOf<SModel>()
        physicalModule.repository?.modelAccess?.runReadAction {
            models = physicalModule.getModelsWithoutDescriptor().toImmutableList()
        }

        monitor.start("Module ${physicalModule.moduleName}", models.size)
        for (model in models) {
            if (monitor.isCanceled) {
                break
            }

            physicalModule.repository?.modelAccess?.runReadAction {
                val modelProgress = monitor.subTask(1)
                modelProgress.start("Model ${model.name}", 1)
                val cloudModel = copyPhysicalModel(treeInRepository, cloudModule, model)
                modelMappingConsumer?.accept(PhysicalToCloudModelMapping(model, cloudModel))
                modelProgress.done()
            }
        }
        monitor.done()
    }

    /**
     * This creates a copy of the given physicalModel under the given cloudModule. It then ensures that it is exactly the same as the given physicalModule.
     * @return the created cloud model
     */
    private fun copyPhysicalModel(treeInRepository: CloudRepository, cloudModule: INode, physicalModel: SModel): INode {
        // TODO fixme. Problem SModelAsNode.wrap does not exist anymore in modelix...
        // SModelAsNode.wrap(physicalModel);
        val originalModel: INode = null!!

        val modelConcept: SConcept = null!! // TODO convert BuiltinLanguages.MPSRepositoryConcepts.Model to SConcept
        val containmentLink: SContainmentLink = null!! // TODO convert BuiltinLanguages.MPSRepositoryConcepts.Module.models to SContainmentLink
        return treeInRepository.createNode(cloudModule, containmentLink, modelConcept) { cloudModel: INode ->
            cloudModel.copyProperty(originalModel, BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            cloudModel.copyProperty(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.id)

            cloudModel.cloneChildren(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)
            cloudModel.cloneChildren(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)

            physicalModel.rootNodes.forEach { physicalRoot ->
                // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
                // cloudModel.addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes, -1, SConceptAdapter.wrap(physicalRoot.getConcept()));
                val cloudRoot: INode = null!!
                replicatePhysicalNode(cloudRoot, physicalRoot)
            }
        }
    }

    /**
     * This takes a cloud node already created and a physical node.
     * It then ensures that the cloud node is exactly as the original physical node.
     * It operates recursively on children.
     */
    private fun replicatePhysicalNode(cloudNode: INode, physicalNode: SNode) {
        cloudNode.mapToMpsNode(physicalNode)

        physicalNode.properties.forEach { prop ->
            cloudNode.setPropertyValue(PropertyFromName(prop.name), physicalNode.getProperty(prop))
        }

        physicalNode.references.forEach { ref ->
            // TODO fix parameter. Problem SNodeToNodeAdapter.wrap does not exist anymore in modelix...
            // SNodeToNodeAdapter.wrap(ref.targetNode)
            val target: INode = null!!
            cloudNode.setReferenceTarget(ref.role, target)
        }

        physicalNode.children.forEach { physicalChild ->
            // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
            // cloudNode.addNewChild(physicalChild.containmentLink?.name, -1, SConceptAdapter.wrap(physicalChild.getConcept()));
            val cloudChild: INode = null!!
            replicatePhysicalNode(cloudChild, physicalChild)
        }
    }
}
