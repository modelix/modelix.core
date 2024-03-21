/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import jetbrains.mps.project.ModuleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.plugin.ModelSyncService

/**
 * This component handles the storage of the cloud configuration.
 * For information about component persistence refer to https://jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html
 * The component takes a snapshot (in form of a State) when a project is closed, and restores it on startup.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    // TODO see what happens when we switch between projects. If the state gets persisted and/or overriden by the info from another project.
    storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
    reloadable = true,
)
class CloudResourcesConfigurationComponent():
    PersistentStateComponent<CloudResourcesConfigurationComponent.State> {

    override fun getState(): State {
        return State().getCurrentState()
    }
    override fun loadState(newState: State) {
        newState.load()
    }

    /**
     * States are capable of taking a snapshot of the current bindings to modelix servers with getCurrentState(),
     * and recreating that state by calling load(). Note that currently the bindings of the state will be added during load(),
     * but no unincluded bindings will be disconnected.
     *
     * States will be automatically saved when a project is closed, and automatically loaded, when that project is reopened.
     * Technically it should also be possible to create a state yourself in order to load it, although this is not what they were made for.
     */
    class State {
        // Mutable collections will not be persisted!!!
        // Maps and Collections will only be persisted 2 layers deep (List<List<String>> works, but List<List<List<String>>> not)
        // Pairs will not be persisted

        // These four lists are linked in the way, that they have the same size and each index references related information in all lists.
        var clients: List<String> = listOf()
        var branchNames: List<String> = listOf()
        var repositoryIds: List<String> = listOf()
        var localVersions: List<String> = listOf()

        // Name, ID
        var moduleNames: List<String> = listOf()
        var moduleIds: List<String> = listOf()

        var modelIds: Collection<String> = listOf()

        fun getCurrentState(): State {
            for (replicatedModel in service<ModelSyncService>().syncService.get_ReplicatedModels()) {
                // TODO is there a better way then a dirty cast?
                clients = clients + (replicatedModel.client as ModelClientV2).baseUrl
                branchNames = branchNames + replicatedModel.branchRef.branchName
                repositoryIds = repositoryIds + replicatedModel.branchRef.repositoryId.id
                CoroutineScope(Dispatchers.IO).launch {
                    localVersions = localVersions + replicatedModel.getCurrentVersion().getContentHash()
                }
            }

            for (moduleBinding in BindingsRegistry.getModuleBindings()) {
                moduleNames = moduleNames + moduleBinding.module.moduleName!!
                moduleIds = moduleIds + moduleBinding.module.moduleId.toString()
            }

            for (modelBinding in BindingsRegistry.getModelBindings()) {
                modelIds += modelBinding.model.modelId.toString()
            }

            return this
        }


        fun load() {
            val sRepository = ActiveMpsProjectInjector.activeMpsProject!!.repository
            val modules: MutableList<SModule> = mutableListOf()
            for (moduleId in moduleIds) {
                val id = PersistenceFacade.getInstance().createModuleId(moduleId)
                val module = sRepository.getModule(id as ModuleId)
                assert(module != null) { "Could not restore module from id. [id = ${id}]" }
                modules.add(module!!)
            }
            val models: MutableList<SModel> = mutableListOf()
            for (modelId in modelIds) {
                val id = PersistenceFacade.getInstance().createModelId(modelId)
                val model = sRepository.getModel(id)
                assert(model != null) { "Could not restore model from id. [id = ${id}]" }
                models.add(model!!)
            }

            val syncService = service<ModelSyncService>()
            CoroutineScope(Dispatchers.IO).launch {
                for (i in clients.indices) {
                    val url = clients[i]
                    val branchName = branchNames[i]
                    val repositoryId = repositoryIds[i]
                    val version = localVersions[i]

                    // TODO BUG this will bind ALL modules to ALL clients
                    for (module in modules) {
                        syncService.rebindModule(url, branchName, module, repositoryId, version)
                    }

                }
            }
        }
    }
}
