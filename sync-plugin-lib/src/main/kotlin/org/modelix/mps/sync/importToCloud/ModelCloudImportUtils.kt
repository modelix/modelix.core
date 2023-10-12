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

import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.MPSProject
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.util.ProgressMonitor
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PropertyFromName
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSModelAsNode
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.synchronization.PhysicalToCloudModelMapping
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.util.cloneChildren
import org.modelix.mps.sync.util.copyProperty
import org.modelix.mps.sync.util.getModelsWithoutDescriptor
import org.modelix.mps.sync.util.mapToMpsNode
import java.util.function.Consumer

// status: ready to test
/**
 * This class is responsible for importing local MPS modules into the Modelix server
 */
object ModelCloudImportUtils {

    fun bindCloudProjectToMpsProject(
        repositoryInModelServer: CloudRepository,
        cloudProjectId: Long,
        mpsProject: MPSProject,
        initialSyncDirection: SyncDirection,
    ) = repositoryInModelServer.addBinding(ProjectBinding(mpsProject, cloudProjectId, initialSyncDirection))

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
        val sModuleAsNode = MPSModuleAsNode.wrap(physicalModule)!!

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
        val originalModel = MPSModelAsNode.wrap(physicalModel)!!
        return treeInRepository.createNode(
            cloudModule,
            BuiltinLanguages.MPSRepositoryConcepts.Module.models,
            BuiltinLanguages.MPSRepositoryConcepts.Model,
        ) { cloudModel: INode ->
            cloudModel.copyProperty(originalModel, BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            cloudModel.copyProperty(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.id)

            cloudModel.cloneChildren(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)
            cloudModel.cloneChildren(originalModel, BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)

            physicalModel.rootNodes.forEach { physicalRoot ->
                val cloudRoot = cloudModel.addNewChild(
                    BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes,
                    -1,
                    MPSConcept.wrap(physicalRoot.concept),
                )
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
            val target = MPSNode.wrap(ref.targetNode)
            cloudNode.setReferenceTarget(ref.role, target)
        }

        physicalNode.children.forEach { physicalChild ->
            val cloudChild =
                cloudNode.addNewChild(physicalChild.containmentLink?.name, -1, MPSConcept.wrap(physicalChild.concept))
            replicatePhysicalNode(cloudChild, physicalChild)
        }
    }
}
