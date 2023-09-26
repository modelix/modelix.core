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

package org.modelix.mps.sync.plugin.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.project.ProjectHelper
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.binding.ModuleBinding
import org.modelix.mps.sync.binding.ProjectModuleBinding
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.history.CloudNodeTreeNode
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.transient.TransientModuleBinding
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.function.Consumer

// status: migrated, but needs some bugfixes
class PersistedBindingConfiguration private constructor(val project: Project) {

    companion object {
        private val logger = mu.KotlinLogging.logger {}
        private val instances = mutableMapOf<Project, PersistedBindingConfiguration>()

        fun getInstance(project: Project): PersistedBindingConfiguration {
            if (!instances.containsKey(project)) {
                instances[project] = PersistedBindingConfiguration(project)
            }
            return instances[project]!!
        }

        fun disposeInstance(project: Project?) {
            val instance = instances.remove(project)
            instance?.dispose()
        }

        private fun ensureModelServerIsPresent(url: String) =
            ModelServerConnections.instance.ensureModelServerIsPresent(url)

        private fun withConnectedCloudRepoHelper(
            modelServer: ModelServerConnection,
            consumer: Consumer<ModelServerConnection>,
            nAttempts: Int,
        ) {
            if (modelServer.isConnected()) {
                consumer.accept(modelServer)
            } else {
                if (nAttempts <= 0) {
                    // TODO this message was logged as: message error "..."
                    logger.error { "Unable to connect to Modelix server. Modelix configuration aborted" }
                    return
                }
                modelServer.reconnect()
                Thread {
                    try {
                        Thread.sleep(250)
                    } catch (_: InterruptedException) {
                    }
                    withConnectedCloudRepoHelper(modelServer, consumer, nAttempts - 1)
                }.start()
            }
        }

        /**
         * Sometimes we need to wait for the repository to be connected. This is the case for example on starting the plugin.
         */
        private fun withConnectedCloudRepo(
            modelServer: ModelServerConnection,
            consumer: Consumer<ModelServerConnection>,
        ) = withConnectedCloudRepoHelper(modelServer, consumer, 20)

        /**
         * FIXME we should probably not identify modules by name but some unique identifier instead
         */
        private fun bindToTransientModules(repositoryInModelServer: CloudRepository, modulesToBind: Set<String>) =
            SharedExecutors.FIXED.execute {
                repositoryInModelServer.runRead { rootNode ->
                    rootNode.allChildren.forEach { child ->
                        val nameProperty = PropertyFromName("name")
                        val name = child.getPropertyValue(nameProperty)
                        if (modulesToBind.contains(name)) {
                            repositoryInModelServer.addTransientModuleBinding(child)
                        }
                    }
                }
            }
    }

    private val logger = mu.KotlinLogging.logger {}

    fun dispose() {
        // TODO fixme
        /*val lightServices = ReflectionUtil.readField(
            ComponentManagerImpl::class.java,
            project as ComponentManagerImpl,
            "lightServices",
        ) as ConcurrentMap<*, *>
        lightServices.remove(CloudResourcesConfigurationComponent::class.java)*/
    }

    fun describeState(): String = readState().toString()

    fun applyToProject() {
        addModelServersAsSpecifiedInPersistedBindingConfiguration()
        bindTransientModulesAsSpecifiedInPersistedConfiguration()
        bindMappedModulesAsSpecifiedInPersistedConfiguration()
    }

    fun isEmpty(): Boolean {
        val state = readState()
        return if (state.modelServers.isNotEmpty()) {
            false
        } else if (state.mappedModules.isNotEmpty()) {
            false
        } else if (state.transientModules.isNotEmpty()) {
            false
        } else if (state.transientProjects.isNotEmpty()) {
            false
        } else {
            true
        }
    }

    fun hasMappedModule(moduleName: String): Boolean {
        val state = readState()
        state.mappedModules.forEach {
            if (it.endsWith("#$moduleName")) {
                return true
            }
        }
        return false
    }

    fun clear() {
        modifyState { state ->
            state.modelServers.clear()
            state.mappedModules.clear()
            state.transientModules.clear()
            state.transientProjects.clear()
        }
        assert(isEmpty())
    }

    private fun readState(): CloudResourcesConfigurationComponent.State {
        // TODO project.getService(Class) is not found, however the CTRL+click navigation can go to the method definition...
        // project.getService(CloudResourcesConfigurationComponent::class.java)
        val cloudResourcesConfigurationComponent: CloudResourcesConfigurationComponent = null!!
        return cloudResourcesConfigurationComponent.state
    }

    private fun modifyState(modifier: Consumer<CloudResourcesConfigurationComponent.State>) {
        // TODO project.getService(Class) is not found, however the CTRL+click navigation can go to the method definition...
        // project.getService(CloudResourcesConfigurationComponent::class.java)
        val cloudResourcesConfigurationComponent: CloudResourcesConfigurationComponent = null!!
        val state = readState()
        modifier.accept(state)
        cloudResourcesConfigurationComponent.loadState(state)
    }

    fun addModelServer(modelServer: ModelServerConnection) =
        modifyState { state -> state.modelServers.add(modelServer.baseUrl) }

    fun isModelServerPresent(url: String) = readState().modelServers.contains(url)

    fun ensureModelServerIsPresent(modelServer: ModelServerConnection) {
        if (!isModelServerPresent(modelServer.baseUrl)) {
            modifyState { state -> state.modelServers.add(modelServer.baseUrl) }
        }
    }

    fun removeModelServer(modelServer: ModelServerConnection) = modifyState { state ->
        state.modelServers.removeIf { url -> url == modelServer.baseUrl }
        state.transientModules.removeIf { moduleStr -> moduleStr.startsWith(modelServer.baseUrl + "#") }
    }

    fun addTransientBoundModule(repositoryInModelServer: CloudRepository, nodeTreeNode: CloudNodeTreeNode) {
        addTransientBoundModule(repositoryInModelServer, nodeTreeNode.branch, nodeTreeNode.node)
    }

    fun removeTransientBoundModule(repositoryInModelServer: CloudRepository, nodeTreeNode: CloudNodeTreeNode) {
        removeTransientBoundModule(repositoryInModelServer, nodeTreeNode.branch, nodeTreeNode.node)
    }

    fun removeBoundModule(repositoryInModelServer: CloudRepository, moduleBinding: ModuleBinding) {
        when (moduleBinding) {
            is TransientModuleBinding -> {
                removeMappedBoundModule(repositoryInModelServer, moduleBinding.moduleNodeId)
            }

            is ProjectModuleBinding -> {
                removeMappedModule(repositoryInModelServer, moduleBinding)
            }

            else -> {
                throw UnsupportedOperationException("Unsupported ModuleBinding ${moduleBinding.javaClass}")
            }
        }
    }

    fun removeMappedModule(repositoryInModelServer: CloudRepository, binding: ProjectModuleBinding) {
        removeMappedBoundModule(repositoryInModelServer, binding.moduleNodeId)
    }

    fun addTransientBoundModule(repositoryInModelServer: CloudRepository, branch: IBranch, cloudNode: INode) =
        modifyState { state ->
            PArea(branch).executeRead {
                val moduleName = (cloudNode as PNodeAdapter).getPropertyValue("name")
                state.transientModules.add(repositoryInModelServer.completeId() + "#" + moduleName)
            }
        }

    fun removeTransientBoundModule(repositoryInModelServer: CloudRepository, branch: IBranch, cloudNode: INode) =
        modifyState { state ->
            PArea(branch).executeRead {
                val moduleName = (cloudNode as PNodeAdapter).getPropertyValue("name")
                val transientModuleDesc = repositoryInModelServer.completeId() + "#" + moduleName
                state.transientModules.remove(transientModuleDesc)
            }
        }

    fun removeTransientBoundModule(repositoryInModelServer: CloudRepository, branch: IBranch, nodeId: Long) =
        modifyState { state ->
            PArea(branch).executeRead {
                val moduleName = branch.readTransaction.getProperty(nodeId, "name")
                val transientModuleDesc = repositoryInModelServer.completeId() + "#" + moduleName
                state.transientModules.remove(transientModuleDesc)
            }
        }

    fun removeMappedBoundModule(repositoryInModelServer: CloudRepository, nodeId: Long) = modifyState { state ->
        val branch = repositoryInModelServer.getActiveBranch().branch
        PArea(branch).executeRead {
            val moduleName = branch.readTransaction.getProperty(nodeId, "name")
            val moduleDesc = repositoryInModelServer.completeId() + "#" + moduleName
            state.mappedModules.remove(moduleDesc)
        }
    }

    fun addTransientBoundModule(repositoryInModelServer: CloudRepository, branch: IBranch, cloudNodeId: Long) {
        addTransientBoundModule(repositoryInModelServer, branch, PNodeAdapter(cloudNodeId, branch))
    }

    fun addTransientBoundProject(repositoryInModelServer: CloudRepository) =
        modifyState { state -> repositoryInModelServer.runRead { -> state.transientProjects.add(repositoryInModelServer.completeId()) } }

    fun addTransientBoundModule(repositoryInModelServer: CloudRepository, nodeTreeNode: PNodeAdapter) =
        modifyState { state ->
            PArea(nodeTreeNode.branch).executeRead {
                val moduleName = nodeTreeNode.getPropertyValue("name")
                state.transientModules.add(repositoryInModelServer.completeId() + "#" + moduleName)
            }
        }

    fun addMappedBoundModule(repositoryInModelServer: CloudRepository, nodeTreeNode: PNodeAdapter) =
        modifyState { state ->
            PArea(nodeTreeNode.branch).executeRead {
                // TODO instead of "name" it must be property/Module : name/.getName()
                val moduleName = nodeTreeNode.getPropertyValue("name")
                check(moduleName != null) { "module should not have null name" }
                state.mappedModules.add(repositoryInModelServer.completeId() + "#" + moduleName)
            }
        }

    private fun addModelServersAsSpecifiedInPersistedBindingConfiguration() {
        val state = readState()
        state.modelServers.forEach { repoUrl ->
            logger.info { "addModelServersAsSpecifiedInPersistedBindingConfiguration $repoUrl" }
            ensureModelServerIsPresent(repoUrl)
        }
    }

    private fun bindTransientModulesAsSpecifiedInPersistedConfiguration() {
        val state = readState()
        for (moduleStr in state.transientModules) {
            val parts = moduleStr.split("#")
            if (parts.size != 2) {
                logger.error(RuntimeException()) { "The configuration of Modelix is not correct, please check .mps/cloudResources.xml. Module entry: $moduleStr" }
                continue
            }
            val repositoryInModelServer = CloudRepository.fromPresentationString(parts[0])
            val modelServer = ensureModelServerIsPresent(repositoryInModelServer.modelServer.baseUrl)

            withConnectedCloudRepo(
                modelServer,
            ) { bindToTransientModules(repositoryInModelServer, setOf(parts[1])) }
        }
    }

    private fun bindMappedModulesAsSpecifiedInPersistedConfiguration() {
        val state = readState()
        for (moduleStr in state.mappedModules) {
            val parts = moduleStr.split("#")
            if (parts.size != 2) {
                logger.error(RuntimeException()) { "The configuration of Modelix is not correct, please check .mps/cloudResources.xml. Module entry: $moduleStr" }
                continue
            }
            val repositoryInModelServer = CloudRepository.fromPresentationString(parts[0])
            val modelServer = ensureModelServerIsPresent(repositoryInModelServer.modelServer.baseUrl)

            withConnectedCloudRepo(modelServer) { bindToMappedModules(repositoryInModelServer, setOf(parts[1])) }
        }
    }

    /**
     * FIXME we should probably not identify modules by name but some unique identifier instead
     */
    private fun bindToMappedModules(repositoryInModelServer: CloudRepository, modulesToBind: Set<String>) {
        SharedExecutors.FIXED.execute {
            repositoryInModelServer.runRead { rootNode ->
                rootNode.allChildren.forEach { child ->
                    val nameProperty = PropertyFromName("name")
                    val name = child.getPropertyValue(nameProperty)!!
                    if (modulesToBind.contains(name)) {
                        val physicalModule = findPhysicalModule(name)
                        if (physicalModule == null) {
                            Messages.showErrorDialog(
                                project,
                                "We cannot instantiate the mapped binding to $name because the module is missing",
                                "Error on mapped binding",
                            )
                        } else {
                            repositoryInModelServer.addBinding(
                                ProjectModuleBinding(
                                    child.nodeIdAsLong(),
                                    physicalModule,
                                    SyncDirection.TO_MPS,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun findPhysicalModule(moduleName: String): SModule? =
        ProjectHelper.toMPSProject(project)?.projectModules?.firstOrNull {
            it.moduleName == moduleName
        }
}
